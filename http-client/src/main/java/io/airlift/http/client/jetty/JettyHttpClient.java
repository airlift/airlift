package io.airlift.http.client.jetty;

import com.google.common.collect.ImmutableList;
import com.google.common.net.HostAndPort;
import com.google.common.primitives.Ints;
import io.airlift.http.client.BodyGenerator;
import io.airlift.http.client.FileBodyGenerator;
import io.airlift.http.client.HttpClientConfig;
import io.airlift.http.client.HttpRequestFilter;
import io.airlift.http.client.Request;
import io.airlift.http.client.RequestStats;
import io.airlift.http.client.ResponseHandler;
import io.airlift.http.client.StaticBodyGenerator;
import io.airlift.http.client.jetty.HttpClientLogger.RequestInfo;
import io.airlift.http.client.jetty.HttpClientLogger.ResponseInfo;
import io.airlift.http.client.spnego.KerberosConfig;
import io.airlift.http.client.spnego.SpnegoAuthenticationProtocolHandler;
import io.airlift.units.Duration;
import org.eclipse.jetty.client.DuplexConnectionPool;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.HttpClientTransport;
import org.eclipse.jetty.client.HttpExchange;
import org.eclipse.jetty.client.HttpRequest;
import org.eclipse.jetty.client.PoolingHttpDestination;
import org.eclipse.jetty.client.Socks4Proxy;
import org.eclipse.jetty.client.WWWAuthenticationProtocolHandler;
import org.eclipse.jetty.client.api.ContentProvider;
import org.eclipse.jetty.client.api.Destination;
import org.eclipse.jetty.client.api.Response;
import org.eclipse.jetty.client.http.HttpClientTransportOverHTTP;
import org.eclipse.jetty.client.http.HttpConnectionOverHTTP;
import org.eclipse.jetty.client.util.BytesContentProvider;
import org.eclipse.jetty.client.util.InputStreamResponseListener;
import org.eclipse.jetty.client.util.PathContentProvider;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http2.client.HTTP2Client;
import org.eclipse.jetty.http2.client.http.HttpClientTransportOverHTTP2;
import org.eclipse.jetty.io.ByteBufferPool;
import org.eclipse.jetty.io.ConnectionStatistics;
import org.eclipse.jetty.io.MappedByteBufferPool;
import org.eclipse.jetty.util.HttpCookieStore;
import org.eclipse.jetty.util.component.LifeCycle;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.eclipse.jetty.util.thread.ScheduledExecutorScheduler;
import org.eclipse.jetty.util.thread.Scheduler;
import org.eclipse.jetty.util.thread.Sweeper;
import org.weakref.jmx.Flatten;
import org.weakref.jmx.Managed;
import org.weakref.jmx.Nested;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static com.google.common.base.MoreObjects.toStringHelper;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Throwables.throwIfUnchecked;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.lang.Math.max;
import static java.lang.Math.toIntExact;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

public class JettyHttpClient
        implements io.airlift.http.client.HttpClient
{
    static {
        JettyLogging.setup();
    }

    private static final String PRESTO_STATS_KEY = "presto_stats";
    private static final long SWEEP_PERIOD_MILLIS = 5000;

    private static final AtomicLong NAME_COUNTER = new AtomicLong();

    private final HttpClient httpClient;
    private final long maxContentLength;
    private final long requestTimeoutMillis;
    private final long idleTimeoutMillis;
    private final boolean recordRequestComplete;
    private final QueuedThreadPoolMBean queuedThreadPoolMBean;
    private final ConnectionStats connectionStats;
    private final RequestStats stats = new RequestStats();
    private final CachedDistribution queuedRequestsPerDestination;
    private final CachedDistribution activeConnectionsPerDestination;
    private final CachedDistribution idleConnectionsPerDestination;

    private final CachedDistribution currentQueuedTime;
    private final CachedDistribution currentRequestTime;
    private final CachedDistribution currentRequestSendTime;
    private final CachedDistribution currentResponseWaitTime;
    private final CachedDistribution currentResponseProcessTime;

    private final List<HttpRequestFilter> requestFilters;
    private final Exception creationLocation = new Exception();
    private final String name;

    private final HttpClientLogger requestLogger;

    public JettyHttpClient()
    {
        this(new HttpClientConfig());
    }

    public JettyHttpClient(HttpClientConfig config)
    {
        this(uniqueName(), config);
    }

    public JettyHttpClient(String name, HttpClientConfig config)
    {
        this(name, config, new KerberosConfig(), ImmutableList.of());
    }

    public JettyHttpClient(
            String name,
            HttpClientConfig config,
            KerberosConfig kerberosConfig,
            Iterable<? extends HttpRequestFilter> requestFilters)
    {
        this.name = requireNonNull(name, "name is null");

        requireNonNull(config, "config is null");
        requireNonNull(requestFilters, "requestFilters is null");

        maxContentLength = config.getMaxContentLength().toBytes();
        requestTimeoutMillis = config.getRequestTimeout().toMillis();
        idleTimeoutMillis = config.getIdleTimeout().toMillis();
        recordRequestComplete = config.getRecordRequestComplete();

        creationLocation.fillInStackTrace();

        SslContextFactory sslContextFactory = new SslContextFactory();
        sslContextFactory.setEndpointIdentificationAlgorithm("HTTPS");
        if (config.getKeyStorePath() != null) {
            sslContextFactory.setKeyStorePath(config.getKeyStorePath());
            sslContextFactory.setKeyStorePassword(config.getKeyStorePassword());
        }
        if (config.getTrustStorePath() != null) {
            sslContextFactory.setTrustStorePath(config.getTrustStorePath());
            sslContextFactory.setTrustStorePassword(config.getTrustStorePassword());
        }
        sslContextFactory.setSecureRandomAlgorithm(config.getSecureRandomAlgorithm());

        HttpClientTransport transport;
        if (config.isHttp2Enabled()) {
            HTTP2Client client = new HTTP2Client();
            client.setInitialSessionRecvWindow(toIntExact(config.getHttp2InitialSessionReceiveWindowSize().toBytes()));
            client.setInitialStreamRecvWindow(toIntExact(config.getHttp2InitialStreamReceiveWindowSize().toBytes()));
            client.setInputBufferSize(toIntExact(config.getHttp2InputBufferSize().toBytes()));
            client.setSelectors(config.getSelectorCount());
            transport = new HttpClientTransportOverHTTP2(client);
        }
        else {
            transport = new HttpClientTransportOverHTTP(config.getSelectorCount());
        }

        if (config.getAuthenticationEnabled()) {
            requireNonNull(kerberosConfig.getConfig(), "kerberos config path is null");
            requireNonNull(config.getKerberosRemoteServiceName(), "kerberos remote service name is null");
            httpClient = new SpnegoHttpClient(kerberosConfig, config, transport, sslContextFactory);
            httpClient.getProtocolHandlers().remove(WWWAuthenticationProtocolHandler.NAME);
            httpClient.getProtocolHandlers().put(new SpnegoAuthenticationProtocolHandler(httpClient));
        }
        else {
            httpClient = new HttpClient(transport, sslContextFactory);
        }

        httpClient.setMaxConnectionsPerDestination(config.getMaxConnectionsPerServer());
        httpClient.setMaxRequestsQueuedPerDestination(config.getMaxRequestsQueuedPerDestination());

        // disable cookies
        httpClient.setCookieStore(new HttpCookieStore.Empty());

        // timeouts
        httpClient.setIdleTimeout(idleTimeoutMillis);
        httpClient.setConnectTimeout(config.getConnectTimeout().toMillis());
        httpClient.setAddressResolutionTimeout(config.getConnectTimeout().toMillis());

        httpClient.setConnectBlocking(config.isConnectBlocking());

        HostAndPort socksProxy = config.getSocksProxy();
        if (socksProxy != null) {
            httpClient.getProxyConfiguration().getProxies().add(new Socks4Proxy(socksProxy.getHost(), socksProxy.getPortOrDefault(1080)));
        }

        httpClient.setByteBufferPool(new MappedByteBufferPool());
        QueuedThreadPool queuedThreadPool = createExecutor(name, config.getMinThreads(), config.getMaxThreads());
        httpClient.setExecutor(queuedThreadPool);
        // add queuedThreadPool as a managed bean to get its state in the client dumps
        httpClient.addBean(queuedThreadPool, true);
        httpClient.setScheduler(createScheduler(name, config.getTimeoutConcurrency(), config.getTimeoutThreads()));

        httpClient.setSocketAddressResolver(new JettyAsyncSocketAddressResolver(
                httpClient.getExecutor(),
                httpClient.getScheduler(),
                config.getConnectTimeout().toMillis()));

        // Jetty client connections can sometimes get stuck while closing which reduces
        // the available connections.  The Jetty Sweeper periodically scans the active
        // connection pool looking for connections in the closed state, and if a connection
        // is observed in the closed state multiple times, it logs, and destroys the connection.
        httpClient.addBean(new Sweeper(httpClient.getScheduler(), SWEEP_PERIOD_MILLIS), true);

        // track connection statistics
        ConnectionStatistics connectionStats = new ConnectionStatistics();
        httpClient.addBean(connectionStats);
        this.connectionStats = new ConnectionStats(connectionStats);

        // configure logging
        if (config.isLogEnabled()) {
            String logFilePath = Paths.get(config.getLogPath(), format("%s-http-client.log", name)).toAbsolutePath().toString();
            requestLogger = new DefaultHttpClientLogger(
                    logFilePath,
                    config.getLogHistory(),
                    config.getLogQueueSize(),
                    config.getLogBufferSize(),
                    config.getLogMaxFileSize().toBytes(),
                    config.isLogCompressionEnabled());
        }
        else {
            requestLogger = new NoopLogger();
        }

        try {
            httpClient.start();

            // remove the GZIP encoding from the client
            // TODO: there should be a better way to to do this
            httpClient.getContentDecoderFactories().clear();
        }
        catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throwIfUnchecked(e);
            throw new RuntimeException(e);
        }

        this.requestFilters = ImmutableList.copyOf(requestFilters);

        this.queuedThreadPoolMBean = new QueuedThreadPoolMBean((QueuedThreadPool) httpClient.getExecutor());

        this.activeConnectionsPerDestination = new ConnectionPoolDistribution(httpClient,
                (distribution, connectionPool) -> distribution.add(connectionPool.getActiveConnections().size()));

        this.idleConnectionsPerDestination = new ConnectionPoolDistribution(httpClient,
                (distribution, connectionPool) -> distribution.add(connectionPool.getIdleConnections().size()));

        this.queuedRequestsPerDestination = new DestinationDistribution(httpClient,
                (distribution, destination) -> distribution.add(destination.getHttpExchanges().size()));

        this.currentQueuedTime = new RequestDistribution(httpClient, (distribution, listener, now) -> {
            long started = listener.getRequestStarted();
            if (started == 0) {
                started = now;
            }
            distribution.add(NANOSECONDS.toMillis(started - listener.getCreated()));
        });

        this.currentRequestTime = new RequestDistribution(httpClient, (distribution, listener, now) -> {
            long started = listener.getRequestStarted();
            if (started == 0) {
                return;
            }
            long finished = listener.getResponseFinished();
            if (finished == 0) {
                finished = now;
            }
            distribution.add(NANOSECONDS.toMillis(finished - started));
        });

        this.currentRequestSendTime = new RequestDistribution(httpClient, (distribution, listener, now) -> {
            long started = listener.getRequestStarted();
            if (started == 0) {
                return;
            }
            long requestSent = listener.getRequestFinished();
            if (requestSent == 0) {
                requestSent = now;
            }
            distribution.add(NANOSECONDS.toMillis(requestSent - started));
        });

        this.currentResponseWaitTime = new RequestDistribution(httpClient, (distribution, listener, now) -> {
            long requestSent = listener.getRequestFinished();
            if (requestSent == 0) {
                return;
            }
            long responseStarted = listener.getResponseStarted();
            if (responseStarted == 0) {
                responseStarted = now;
            }
            distribution.add(NANOSECONDS.toMillis(responseStarted - requestSent));
        });

        this.currentResponseProcessTime = new RequestDistribution(httpClient, (distribution, listener, now) -> {
            long responseStarted = listener.getResponseStarted();
            if (responseStarted == 0) {
                return;
            }
            long finished = listener.getResponseFinished();
            if (finished == 0) {
                finished = now;
            }
            distribution.add(NANOSECONDS.toMillis(finished - responseStarted));
        });
    }

    private static QueuedThreadPool createExecutor(String name, int minThreads, int maxThreads)
    {
        try {
            QueuedThreadPool pool = new QueuedThreadPool(maxThreads, minThreads, 60000, null);
            pool.setName("http-client-" + name);
            pool.setDaemon(true);
            pool.start();
            pool.setStopTimeout(2000);
            pool.setDetailedDump(true);
            return pool;
        }
        catch (Exception e) {
            throwIfUnchecked(e);
            throw new RuntimeException(e);
        }
    }

    private static Scheduler createScheduler(String name, int timeoutConcurrency, int timeoutThreads)
    {
        Scheduler scheduler;
        String threadName = "http-client-" + name + "-scheduler";
        if ((timeoutConcurrency == 1) && (timeoutThreads == 1)) {
            scheduler = new ScheduledExecutorScheduler(threadName, true);
        }
        else {
            checkArgument(timeoutConcurrency >= 1, "timeoutConcurrency must be at least one");
            int threads = max(1, timeoutThreads / timeoutConcurrency);
            scheduler = new ConcurrentScheduler(timeoutConcurrency, threads, threadName);
        }

        try {
            scheduler.start();
        }
        catch (Exception e) {
            throwIfUnchecked(e);
            throw new RuntimeException(e);
        }

        return scheduler;
    }

    @Override
    public <T, E extends Exception> T execute(Request request, ResponseHandler<T, E> responseHandler)
            throws E
    {
        long requestStart = System.nanoTime();

        // apply filters
        request = applyRequestFilters(request);

        // create jetty request and response listener
        HttpRequest jettyRequest = buildJettyRequest(request);
        InputStreamResponseListener listener = new InputStreamResponseListener()
        {
            @Override
            public void onContent(Response response, ByteBuffer content)
            {
                // ignore empty blocks
                if (content.remaining() == 0) {
                    return;
                }
                super.onContent(response, content);
            }
        };

        RequestInfo requestInfo = RequestInfo.from(jettyRequest, System.currentTimeMillis());

        // fire the request
        jettyRequest.send(listener);

        // wait for response to begin
        Response response;
        try {
            response = listener.get(httpClient.getIdleTimeout(), MILLISECONDS);
        }
        catch (InterruptedException e) {
            stats.recordRequestFailed();
            requestLogger.log(requestInfo, ResponseInfo.failed(Optional.empty(), Optional.of(e)));
            jettyRequest.abort(e);
            Thread.currentThread().interrupt();
            return responseHandler.handleException(request, e);
        }
        catch (TimeoutException e) {
            stats.recordRequestFailed();
            requestLogger.log(requestInfo, ResponseInfo.failed(Optional.empty(), Optional.of(e)));
            jettyRequest.abort(e);
            return responseHandler.handleException(request, e);
        }
        catch (ExecutionException e) {
            stats.recordRequestFailed();
            requestLogger.log(requestInfo, ResponseInfo.failed(Optional.empty(), Optional.of(e)));
            Throwable cause = e.getCause();
            if (cause instanceof Exception) {
                return responseHandler.handleException(request, (Exception) cause);
            }
            else if ((cause instanceof NoClassDefFoundError) && cause.getMessage().endsWith("ALPNClientConnection")) {
                return responseHandler.handleException(request, new RuntimeException("HTTPS cannot be used when HTTP/2 is enabled", cause));
            }
            else {
                return responseHandler.handleException(request, new RuntimeException(cause));
            }
        }

        // process response
        long responseStart = System.nanoTime();

        JettyResponse jettyResponse = null;
        T value;
        try {
            jettyResponse = new JettyResponse(response, listener.getInputStream());
            value = responseHandler.handle(request, jettyResponse);
        }
        finally {
            if (recordRequestComplete) {
                recordRequestComplete(stats, request, requestStart, jettyResponse, responseStart);
            }
        }
        requestLogger.log(requestInfo, ResponseInfo.from(Optional.of(response), jettyResponse.getBytesRead()));
        return value;
    }

    @Override
    public <T, E extends Exception> HttpResponseFuture<T> executeAsync(Request request, ResponseHandler<T, E> responseHandler)
    {
        requireNonNull(request, "request is null");
        requireNonNull(responseHandler, "responseHandler is null");

        request = applyRequestFilters(request);

        HttpRequest jettyRequest = buildJettyRequest(request);

        RequestInfo requestInfo = RequestInfo.from(jettyRequest, System.currentTimeMillis());

        JettyResponseFuture<T, E> future = new JettyResponseFuture<>(request, jettyRequest, responseHandler, stats, recordRequestComplete);

        BufferingResponseListener listener = new BufferingResponseListener(future,
                Ints.saturatedCast(maxContentLength),
                responseInfo -> requestLogger.log(requestInfo, responseInfo));

        try {
            jettyRequest.send(listener);
        }
        catch (RuntimeException e) {
            if (!(e instanceof RejectedExecutionException)) {
                e = new RejectedExecutionException(e);
            }
            // normally this is a rejected execution exception because the client has been closed
            future.failed(e);
            requestLogger.log(requestInfo, ResponseInfo.failed(Optional.empty(), Optional.of(e)));
        }
        return future;
    }

    private Request applyRequestFilters(Request request)
    {
        for (HttpRequestFilter requestFilter : requestFilters) {
            request = requestFilter.filterRequest(request);
        }
        return request;
    }

    private HttpRequest buildJettyRequest(Request finalRequest)
    {
        HttpRequest jettyRequest = (HttpRequest) httpClient.newRequest(finalRequest.getUri());

        JettyRequestListener listener = new JettyRequestListener(finalRequest.getUri());
        jettyRequest.onRequestBegin(request -> listener.onRequestBegin());
        jettyRequest.onRequestSuccess(request -> listener.onRequestEnd());
        jettyRequest.onResponseBegin(response -> listener.onResponseBegin());
        jettyRequest.onComplete(result -> listener.onFinish());
        jettyRequest.attribute(PRESTO_STATS_KEY, listener);

        // jetty client always adds the user agent header
        // todo should there be a default?
        jettyRequest.getHeaders().remove(HttpHeader.USER_AGENT);

        jettyRequest.method(finalRequest.getMethod());

        for (Entry<String, String> entry : finalRequest.getHeaders().entries()) {
            jettyRequest.header(entry.getKey(), entry.getValue());
        }

        BodyGenerator bodyGenerator = finalRequest.getBodyGenerator();
        if (bodyGenerator != null) {
            if (bodyGenerator instanceof StaticBodyGenerator) {
                StaticBodyGenerator staticBodyGenerator = (StaticBodyGenerator) bodyGenerator;
                jettyRequest.content(new BytesContentProvider(staticBodyGenerator.getBody()));
            }
            else if (bodyGenerator instanceof FileBodyGenerator) {
                Path path = ((FileBodyGenerator) bodyGenerator).getPath();
                jettyRequest.content(fileContentProvider(path));
            }
            else {
                jettyRequest.content(new BodyGeneratorContentProvider(bodyGenerator, httpClient.getExecutor()));
            }
        }

        // timeouts
        jettyRequest.timeout(requestTimeoutMillis, MILLISECONDS);
        jettyRequest.idleTimeout(idleTimeoutMillis, MILLISECONDS);

        return jettyRequest;
    }

    private static ContentProvider fileContentProvider(Path path)
    {
        try {
            PathContentProvider provider = new PathContentProvider(null, path);
            provider.setByteBufferPool(new ByteBufferPool()
            {
                @Override
                public ByteBuffer acquire(int size, boolean direct)
                {
                    return ByteBuffer.allocate(size);
                }

                @Override
                public void release(ByteBuffer buffer) {}
            });
            return provider;
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public List<HttpRequestFilter> getRequestFilters()
    {
        return requestFilters;
    }

    public long getRequestTimeoutMillis()
    {
        return requestTimeoutMillis;
    }

    @Override
    @Managed
    @Flatten
    public RequestStats getStats()
    {
        return stats;
    }

    @Override
    public long getMaxContentLength()
    {
        return maxContentLength;
    }

    @Managed
    @Nested
    public QueuedThreadPoolMBean getThreadPool()
    {
        return queuedThreadPoolMBean;
    }

    @Managed
    @Nested
    public ConnectionStats getConnectionStats()
    {
        return connectionStats;
    }

    @Managed
    @Nested
    public CachedDistribution getActiveConnectionsPerDestination()
    {
        return activeConnectionsPerDestination;
    }

    @Managed
    @Nested
    public CachedDistribution getIdleConnectionsPerDestination()
    {
        return idleConnectionsPerDestination;
    }

    @Managed
    @Nested
    public CachedDistribution getQueuedRequestsPerDestination()
    {
        return queuedRequestsPerDestination;
    }

    @Managed
    @Nested
    public CachedDistribution getCurrentQueuedTime()
    {
        return currentQueuedTime;
    }

    @Managed
    @Nested
    public CachedDistribution getCurrentRequestTime()
    {
        return currentRequestTime;
    }

    @Managed
    @Nested
    public CachedDistribution getCurrentRequestSendTime()
    {
        return currentRequestSendTime;
    }

    @Managed
    @Nested
    public CachedDistribution getCurrentResponseWaitTime()
    {
        return currentResponseWaitTime;
    }

    @Managed
    @Nested
    public CachedDistribution getCurrentResponseProcessTime()
    {
        return currentResponseProcessTime;
    }

    @Managed
    public String dump()
    {
        return httpClient.dump();
    }

    @Managed
    public void dumpStdErr()
    {
        httpClient.dumpStdErr();
    }

    @Managed
    public String dumpAllDestinations()
    {
        return format("%s\t%s\t%s\t%s\t%s\n", "URI", "queued", "request", "wait", "response") +
                httpClient.getDestinations().stream()
                        .map(JettyHttpClient::dumpDestination)
                        .collect(Collectors.joining("\n"));
    }

    @Managed
    public int getLoggerQueueSize()
    {
        return requestLogger.getQueueSize();
    }

    // todo this should be @Managed but operations with parameters are broken in jmx utils https://github.com/martint/jmxutils/issues/27
    @SuppressWarnings("UnusedDeclaration")
    public String dumpDestination(URI uri)
    {
        Destination destination = httpClient.getDestination(uri.getScheme(), uri.getHost(), uri.getPort());
        if (destination == null) {
            return null;
        }

        return dumpDestination(destination);
    }

    private static String dumpDestination(Destination destination)
    {
        long now = System.nanoTime();
        return getRequestListenersForDestination(destination).stream()
                .map(listener -> dumpRequest(now, listener))
                .sorted()
                .collect(Collectors.joining("\n"));
    }

    static List<JettyRequestListener> getRequestListenersForDestination(Destination destination)
    {
        return getRequestForDestination(destination).stream()
                .map(request -> request.getAttributes().get(PRESTO_STATS_KEY))
                .map(JettyRequestListener.class::cast)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private static List<org.eclipse.jetty.client.api.Request> getRequestForDestination(Destination destination)
    {
        PoolingHttpDestination poolingHttpDestination = (PoolingHttpDestination) destination;
        Queue<HttpExchange> httpExchanges = poolingHttpDestination.getHttpExchanges();

        List<org.eclipse.jetty.client.api.Request> requests = httpExchanges.stream()
                .map(HttpExchange::getRequest)
                .collect(Collectors.toList());

        ((DuplexConnectionPool) poolingHttpDestination.getConnectionPool()).getActiveConnections().stream()
                .filter(HttpConnectionOverHTTP.class::isInstance)
                .map(HttpConnectionOverHTTP.class::cast)
                .map(connection -> connection.getHttpChannel().getHttpExchange())
                .filter(Objects::nonNull)
                .forEach(exchange -> requests.add(exchange.getRequest()));

        return requests.stream()
                .filter(Objects::nonNull)
                .collect(toImmutableList());
    }

    private static String dumpRequest(long now, JettyRequestListener listener)
    {
        long created = listener.getCreated();
        long requestStarted = listener.getRequestStarted();
        if (requestStarted == 0) {
            requestStarted = now;
        }
        long requestFinished = listener.getRequestFinished();
        if (requestFinished == 0) {
            requestFinished = now;
        }
        long responseStarted = listener.getResponseStarted();
        if (responseStarted == 0) {
            responseStarted = now;
        }
        long finished = listener.getResponseFinished();
        if (finished == 0) {
            finished = now;
        }
        return format("%s\t%.1f\t%.1f\t%.1f\t%.1f",
                listener.getUri(),
                nanosToMillis(requestStarted - created),
                nanosToMillis(requestFinished - requestStarted),
                nanosToMillis(responseStarted - requestFinished),
                nanosToMillis(finished - responseStarted));
    }

    private static double nanosToMillis(long nanos)
    {
        return new Duration(nanos, NANOSECONDS).getValue(MILLISECONDS);
    }

    @Override
    public void close()
    {
        // client must be destroyed before the pools or
        // you will create a several second busy wait loop
        closeQuietly(httpClient);
        closeQuietly((LifeCycle) httpClient.getExecutor());
        closeQuietly(httpClient.getScheduler());
        requestLogger.close();
    }

    @Override
    public boolean isClosed()
    {
        return !httpClient.isRunning();
    }

    @Override
    public String toString()
    {
        return toStringHelper(this)
                .addValue(name)
                .toString();
    }

    @SuppressWarnings("UnusedDeclaration")
    public StackTraceElement[] getCreationLocation()
    {
        return creationLocation.getStackTrace();
    }

    private static void closeQuietly(LifeCycle service)
    {
        try {
            if (service != null) {
                service.stop();
            }
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        catch (Exception ignored) {
        }
    }

    private static String uniqueName()
    {
        return "anonymous" + NAME_COUNTER.incrementAndGet();
    }

    static void recordRequestComplete(RequestStats requestStats, Request request, long requestStart, JettyResponse response, long responseStart)
    {
        if (response == null) {
            return;
        }

        Duration responseProcessingTime = Duration.nanosSince(responseStart);
        Duration requestProcessingTime = new Duration(responseStart - requestStart, NANOSECONDS);

        requestStats.recordResponseReceived(request.getMethod(),
                response.getStatusCode(),
                response.getBytesRead(),
                response.getBytesRead(),
                requestProcessingTime,
                responseProcessingTime);
    }
}
