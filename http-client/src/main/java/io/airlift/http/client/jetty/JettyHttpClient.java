package io.airlift.http.client.jetty;

import com.google.common.base.Throwables;
import com.google.common.collect.AbstractIterator;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.io.CountingInputStream;
import com.google.common.net.HostAndPort;
import com.google.common.primitives.Ints;
import com.google.common.util.concurrent.AbstractFuture;
import io.airlift.http.client.BodyGenerator;
import io.airlift.http.client.FileBodyGenerator;
import io.airlift.http.client.GatheringByteArrayInputStream;
import io.airlift.http.client.HeaderName;
import io.airlift.http.client.HttpClientConfig;
import io.airlift.http.client.HttpRequestFilter;
import io.airlift.http.client.Request;
import io.airlift.http.client.RequestStats;
import io.airlift.http.client.ResponseHandler;
import io.airlift.http.client.ResponseTooLargeException;
import io.airlift.http.client.StaticBodyGenerator;
import io.airlift.http.client.spnego.KerberosConfig;
import io.airlift.http.client.spnego.SpnegoAuthentication;
import io.airlift.http.client.spnego.SpnegoAuthenticationStore;
import io.airlift.log.Logger;
import io.airlift.stats.Distribution;
import io.airlift.units.DataSize;
import io.airlift.units.Duration;
import org.eclipse.jetty.client.DuplexConnectionPool;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.HttpClientTransport;
import org.eclipse.jetty.client.HttpExchange;
import org.eclipse.jetty.client.HttpRequest;
import org.eclipse.jetty.client.PoolingHttpDestination;
import org.eclipse.jetty.client.Socks4Proxy;
import org.eclipse.jetty.client.api.AuthenticationStore;
import org.eclipse.jetty.client.api.ContentProvider;
import org.eclipse.jetty.client.api.Destination;
import org.eclipse.jetty.client.api.Response;
import org.eclipse.jetty.client.api.Response.Listener;
import org.eclipse.jetty.client.api.Result;
import org.eclipse.jetty.client.http.HttpClientTransportOverHTTP;
import org.eclipse.jetty.client.http.HttpConnectionOverHTTP;
import org.eclipse.jetty.client.util.BytesContentProvider;
import org.eclipse.jetty.client.util.InputStreamResponseListener;
import org.eclipse.jetty.client.util.PathContentProvider;
import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http2.client.HTTP2Client;
import org.eclipse.jetty.http2.client.http.HttpClientTransportOverHTTP2;
import org.eclipse.jetty.io.ByteBufferPool;
import org.eclipse.jetty.util.HttpCookieStore;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.util.thread.Sweeper;
import org.weakref.jmx.Flatten;
import org.weakref.jmx.Managed;
import org.weakref.jmx.Nested;

import javax.annotation.concurrent.GuardedBy;
import javax.annotation.concurrent.ThreadSafe;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static com.google.common.base.MoreObjects.toStringHelper;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static io.airlift.units.DataSize.Unit.KILOBYTE;
import static io.airlift.units.DataSize.Unit.MEGABYTE;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

public class JettyHttpClient
        implements io.airlift.http.client.HttpClient
{
    static {
        JettyLogging.setup();
    }

    private static final AtomicLong nameCounter = new AtomicLong();
    private static final String PRESTO_STATS_KEY = "presto_stats";
    private static final long SWEEP_PERIOD_MILLIS = 5000;
    private static final String REALM_IN_CHALLENGE = "X-Airlift-Realm-In-Challenge";
    private static final int CLIENT_TRANSPORT_SELECTORS = 2;

    private final Optional<JettyIoPool> anonymousPool;
    private final HttpClient httpClient;
    private final long maxContentLength;
    private final long requestTimeoutMillis;
    private final long idleTimeoutMillis;
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
    private final boolean authenticationEnabled;

    public JettyHttpClient()
    {
        this(new HttpClientConfig(), ImmutableList.<HttpRequestFilter>of());
    }

    public JettyHttpClient(HttpClientConfig config)
    {
        this(config, ImmutableList.<HttpRequestFilter>of());
    }

    public JettyHttpClient(HttpClientConfig config, Iterable<? extends HttpRequestFilter> requestFilters)
    {
        this(config, new KerberosConfig(), Optional.empty(), requestFilters);
    }

    public JettyHttpClient(HttpClientConfig config, JettyIoPool jettyIoPool, Iterable<? extends HttpRequestFilter> requestFilters)
    {
        this(config, new KerberosConfig(), Optional.of(jettyIoPool), requestFilters);
    }

    public JettyHttpClient(
            HttpClientConfig config,
            KerberosConfig kerberosConfig,
            Optional<JettyIoPool> jettyIoPool,
            Iterable<? extends HttpRequestFilter> requestFilters)
    {
        requireNonNull(config, "config is null");
        requireNonNull(jettyIoPool, "jettyIoPool is null");
        requireNonNull(requestFilters, "requestFilters is null");

        maxContentLength = config.getMaxContentLength().toBytes();
        requestTimeoutMillis = config.getRequestTimeout().toMillis();
        idleTimeoutMillis = config.getIdleTimeout().toMillis();
        authenticationEnabled = config.getAuthenticationEnabled();

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

        HttpClientTransport transport;
        if (config.isHttp2Enabled()) {
            HTTP2Client client = new HTTP2Client();
            client.setSelectors(CLIENT_TRANSPORT_SELECTORS);
            transport = new HttpClientTransportOverHTTP2(client);
        }
        else {
            transport = new HttpClientTransportOverHTTP(CLIENT_TRANSPORT_SELECTORS);
        }

        if (authenticationEnabled) {
            requireNonNull(kerberosConfig.getConfig(), "kerberos config path is null");
            requireNonNull(config.getKerberosRemoteServiceName(), "kerberos remote service name is null");
            httpClient = new SpnegoHttpClient(kerberosConfig, config, transport, sslContextFactory);
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

        HostAndPort socksProxy = config.getSocksProxy();
        if (socksProxy != null) {
            httpClient.getProxyConfiguration().getProxies().add(new Socks4Proxy(socksProxy.getHostText(), socksProxy.getPortOrDefault(1080)));
        }

        JettyIoPool pool = jettyIoPool.orElse(null);
        if (pool == null) {
            pool = new JettyIoPool("anonymous" + nameCounter.incrementAndGet(), new JettyIoPoolConfig());
            anonymousPool = Optional.of(pool);
        }
        else {
            anonymousPool = Optional.empty();
        }

        name = pool.getName();
        httpClient.setExecutor(pool.getExecutor());
        httpClient.setByteBufferPool(pool.getByteBufferPool());
        httpClient.setScheduler(pool.getScheduler());

        // Jetty client connections can sometimes get stuck while closing which reduces
        // the available connections.  The Jetty Sweeper periodically scans the active
        // connection pool looking for connections in the closed state, and if a connection
        // is observed in the closed state multiple times, it logs, and destroys the connection.
        httpClient.addBean(new Sweeper(pool.getScheduler(), SWEEP_PERIOD_MILLIS), true);

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
            throw Throwables.propagate(e);
        }

        this.requestFilters = ImmutableList.copyOf(requestFilters);

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

    @Override
    public <T, E extends Exception> T execute(Request request, ResponseHandler<T, E> responseHandler)
            throws E
    {
        long requestStart = System.nanoTime();

        // apply filters
        request = applyRequestFilters(request);

        // create jetty request and response listener
        HttpRequest jettyRequest = buildJettyRequest(request);
        InputStreamResponseListener listener = new InputStreamResponseListener(maxContentLength)
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

        // fire the request
        jettyRequest.send(listener);

        // wait for response to begin
        Response response;
        try {
            response = listener.get(httpClient.getIdleTimeout(), MILLISECONDS);
        }
        catch (InterruptedException e) {
            stats.recordRequestFailed();
            Thread.currentThread().interrupt();
            return responseHandler.handleException(request, e);
        }
        catch (TimeoutException e) {
            stats.recordRequestFailed();
            return responseHandler.handleException(request, e);
        }
        catch (ExecutionException e) {
            stats.recordRequestFailed();
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
            recordRequestComplete(stats, request, requestStart, jettyResponse, responseStart);
        }
        return value;
    }

    @Override
    public <T, E extends Exception> HttpResponseFuture<T> executeAsync(Request request, ResponseHandler<T, E> responseHandler)
    {
        requireNonNull(request, "request is null");
        requireNonNull(responseHandler, "responseHandler is null");

        request = applyRequestFilters(request);

        HttpRequest jettyRequest = buildJettyRequest(request);

        JettyResponseFuture<T, E> future = new JettyResponseFuture<>(request, jettyRequest, responseHandler, stats);

        BufferingResponseListener listener = new BufferingResponseListener(future, Ints.saturatedCast(maxContentLength));

        try {
            jettyRequest.send(listener);
        }
        catch (RuntimeException e) {
            if (!(e instanceof RejectedExecutionException)) {
                e = new RejectedExecutionException(e);
            }
            // normally this is a rejected execution exception because the client has been closed
            future.failed(e);
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

        // client authentications
        if (authenticationEnabled && "https".equalsIgnoreCase(jettyRequest.getURI().getScheme())) {
            // Unlike other clients, Jetty Kerberos client requires the server to include a realm
            // in the challenge from the server. This breaks the SPNEGO protocol. We use a custom
            // header to tell the server to return the required realm.
            // Workaround for https://bugs.eclipse.org/bugs/show_bug.cgi?id=458258
            jettyRequest.header(REALM_IN_CHALLENGE, "true");
        }
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
            throw Throwables.propagate(e);
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
        return String.format("%s\t%s\t%s\t%s\t%s\n", "URI", "queued", "request", "wait", "response") +
                httpClient.getDestinations().stream()
                        .map(JettyHttpClient::dumpDestination)
                        .collect(Collectors.joining("\n"));
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
                .map(request -> dumpRequest(now, request))
                .sorted()
                .collect(Collectors.joining("\n"));
    }

    private static List<JettyRequestListener> getRequestListenersForDestination(Destination destination)
    {
        return getRequestForDestination(destination).stream()
                .map(request -> (JettyRequestListener) request.getAttributes().get(PRESTO_STATS_KEY))
                .filter(listener -> listener != null)
                .collect(Collectors.toList());
    }

    private static List<org.eclipse.jetty.client.api.Request> getRequestForDestination(Destination destination)
    {
        PoolingHttpDestination<?> poolingHttpDestination = (PoolingHttpDestination<?>) destination;
        Queue<HttpExchange> httpExchanges = poolingHttpDestination.getHttpExchanges();

        List<org.eclipse.jetty.client.api.Request> requests = httpExchanges.stream()
                .map(HttpExchange::getRequest)
                .collect(Collectors.toList());

        poolingHttpDestination.getConnectionPool().getActiveConnections().stream()
                .filter(HttpConnectionOverHTTP.class::isInstance)
                .map(connection -> ((HttpConnectionOverHTTP) connection).getHttpChannel().getHttpExchange())
                .filter(exchange -> exchange != null)
                .forEach(exchange -> requests.add(exchange.getRequest()));

        return requests.stream().filter(request -> request != null).collect(Collectors.toList());
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
        return String.format("%s\t%.1f\t%.1f\t%.1f\t%.1f",
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
        try {
            httpClient.stop();
        }
        catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
        catch (Exception ignored) {
        }
        anonymousPool.ifPresent(JettyIoPool::close);
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

    private static class JettyResponse
            implements io.airlift.http.client.Response
    {
        private final Response response;
        private final CountingInputStream inputStream;
        private final ListMultimap<HeaderName, String> headers;

        public JettyResponse(Response response, InputStream inputStream)
        {
            this.response = response;
            this.inputStream = new CountingInputStream(inputStream);
            this.headers = toHeadersMap(response.getHeaders());
        }

        @Override
        public int getStatusCode()
        {
            return response.getStatus();
        }

        @Override
        public String getStatusMessage()
        {
            return response.getReason();
        }

        @Override
        public ListMultimap<HeaderName, String> getHeaders()
        {
            return headers;
        }

        @Override
        public long getBytesRead()
        {
            return inputStream.getCount();
        }

        @Override
        public InputStream getInputStream()
        {
            return inputStream;
        }

        @Override
        public String toString()
        {
            return toStringHelper(this)
                    .add("statusCode", getStatusCode())
                    .add("statusMessage", getStatusMessage())
                    .add("headers", getHeaders())
                    .toString();
        }

        private static ListMultimap<HeaderName, String> toHeadersMap(HttpFields headers)
        {
            ImmutableListMultimap.Builder<HeaderName, String> builder = ImmutableListMultimap.builder();
            for (String name : headers.getFieldNamesCollection()) {
                for (String value : headers.getValuesList(name)) {
                    builder.put(HeaderName.of(name), value);
                }
            }
            return builder.build();
        }
    }

    private static class JettyResponseFuture<T, E extends Exception>
            extends AbstractFuture<T>
            implements HttpResponseFuture<T>
    {
        public enum JettyAsyncHttpState
        {
            WAITING_FOR_CONNECTION,
            SENDING_REQUEST,
            WAITING_FOR_RESPONSE,
            PROCESSING_RESPONSE,
            DONE,
            FAILED,
            CANCELED
        }

        private static final Logger log = Logger.get(JettyResponseFuture.class);

        private final long requestStart = System.nanoTime();
        private final AtomicReference<JettyAsyncHttpState> state = new AtomicReference<>(JettyAsyncHttpState.WAITING_FOR_CONNECTION);
        private final Request request;
        private final org.eclipse.jetty.client.api.Request jettyRequest;
        private final ResponseHandler<T, E> responseHandler;
        private final RequestStats stats;

        public JettyResponseFuture(Request request, org.eclipse.jetty.client.api.Request jettyRequest, ResponseHandler<T, E> responseHandler, RequestStats stats)
        {
            this.request = request;
            this.jettyRequest = jettyRequest;
            this.responseHandler = responseHandler;
            this.stats = stats;
        }

        @Override
        public String getState()
        {
            return state.get().toString();
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning)
        {
            try {
                stats.recordRequestCanceled();
                state.set(JettyAsyncHttpState.CANCELED);
                jettyRequest.abort(new CancellationException());
                return super.cancel(mayInterruptIfRunning);
            }
            catch (Throwable e) {
                setException(e);
                return true;
            }
        }

        protected void completed(Response response, InputStream content)
        {
            if (state.get() == JettyAsyncHttpState.CANCELED) {
                return;
            }

            T value;
            try {
                value = processResponse(response, content);
            }
            catch (Throwable e) {
                // this will be an instance of E from the response handler or an Error
                storeException(e);
                return;
            }
            state.set(JettyAsyncHttpState.DONE);
            set(value);
        }

        private T processResponse(Response response, InputStream content)
                throws E
        {
            // this time will not include the data fetching portion of the response,
            // since the response is fully cached in memory at this point
            long responseStart = System.nanoTime();

            state.set(JettyAsyncHttpState.PROCESSING_RESPONSE);
            JettyResponse jettyResponse = null;
            T value;
            try {
                jettyResponse = new JettyResponse(response, content);
                value = responseHandler.handle(request, jettyResponse);
            }
            finally {
                recordRequestComplete(stats, request, requestStart, jettyResponse, responseStart);
            }
            return value;
        }

        protected void failed(Throwable throwable)
        {
            if (state.get() == JettyAsyncHttpState.CANCELED) {
                return;
            }

            stats.recordRequestFailed();

            // give handler a chance to rewrite the exception or return a value instead
            if (throwable instanceof Exception) {
                try {
                    T value = responseHandler.handleException(request, (Exception) throwable);
                    // handler returned a value, store it in the future
                    state.set(JettyAsyncHttpState.DONE);
                    set(value);
                    return;
                }
                catch (Throwable newThrowable) {
                    throwable = newThrowable;
                }
            }

            // at this point "throwable" will either be an instance of E
            // from the response handler or not an instance of Exception
            storeException(throwable);
        }

        private void storeException(Throwable throwable)
        {
            if (throwable instanceof CancellationException) {
                state.set(JettyAsyncHttpState.CANCELED);
            }
            else {
                state.set(JettyAsyncHttpState.FAILED);
            }
            if (throwable == null) {
                throwable = new Throwable("Throwable is null");
                log.error(throwable, "Something is broken");
            }

            setException(throwable);
        }

        @Override
        public String toString()
        {
            return toStringHelper(this)
                    .add("requestStart", requestStart)
                    .add("state", state)
                    .add("request", request)
                    .toString();
        }
    }

    private static void recordRequestComplete(RequestStats requestStats, Request request, long requestStart, JettyResponse response, long responseStart)
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

    private static class BodyGeneratorContentProvider
            implements ContentProvider
    {
        private static final ByteBuffer DONE = ByteBuffer.allocate(0);
        private static final ByteBuffer EXCEPTION = ByteBuffer.allocate(0);

        private final BodyGenerator bodyGenerator;
        private final Executor executor;

        public BodyGeneratorContentProvider(BodyGenerator bodyGenerator, Executor executor)
        {
            this.bodyGenerator = bodyGenerator;
            this.executor = executor;
        }

        @Override
        public long getLength()
        {
            return -1;
        }

        @Override
        public Iterator<ByteBuffer> iterator()
        {
            final BlockingQueue<ByteBuffer> chunks = new ArrayBlockingQueue<>(16);
            final AtomicReference<Exception> exception = new AtomicReference<>();

            executor.execute(() -> {
                BodyGeneratorOutputStream out = new BodyGeneratorOutputStream(chunks);
                try {
                    bodyGenerator.write(out);
                    out.close();
                }
                catch (Exception e) {
                    exception.set(e);
                    chunks.add(EXCEPTION);
                }
            });

            return new AbstractIterator<ByteBuffer>()
            {
                @Override
                protected ByteBuffer computeNext()
                {
                    ByteBuffer chunk;
                    try {
                        chunk = chunks.take();
                    }
                    catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Interrupted", e);
                    }

                    if (chunk == EXCEPTION) {
                        throw Throwables.propagate(exception.get());
                    }
                    if (chunk == DONE) {
                        return endOfData();
                    }
                    return chunk;
                }
            };
        }

        private final class BodyGeneratorOutputStream
                extends OutputStream
        {
            private final BlockingQueue<ByteBuffer> chunks;

            private BodyGeneratorOutputStream(BlockingQueue<ByteBuffer> chunks)
            {
                this.chunks = chunks;
            }

            @Override
            public void write(int b)
                    throws IOException
            {
                try {
                    // must copy array since it could be reused
                    chunks.put(ByteBuffer.wrap(new byte[] {(byte) b}));
                }
                catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new InterruptedIOException();
                }
            }

            @Override
            public void write(byte[] b, int off, int len)
                    throws IOException
            {
                try {
                    // must copy array since it could be reused
                    byte[] copy = Arrays.copyOfRange(b, off, len);
                    chunks.put(ByteBuffer.wrap(copy));
                }
                catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new InterruptedIOException();
                }
            }

            @Override
            public void close()
                    throws IOException
            {
                try {
                    chunks.put(DONE);
                }
                catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new InterruptedIOException();
                }
            }
        }
    }

    @ThreadSafe
    private static class BufferingResponseListener
            extends Listener.Adapter
    {
        private static final long BUFFER_MAX_BYTES = new DataSize(1, MEGABYTE).toBytes();
        private static final long BUFFER_MIN_BYTES = new DataSize(1, KILOBYTE).toBytes();
        private final JettyResponseFuture<?, ?> future;
        private final int maxLength;

        @GuardedBy("this")
        private byte[] currentBuffer = new byte[0];
        @GuardedBy("this")
        private int currentBufferPosition;
        @GuardedBy("this")
        private List<byte[]> buffers = new ArrayList<>();
        @GuardedBy("this")
        private long size;

        public BufferingResponseListener(JettyResponseFuture<?, ?> future, int maxLength)
        {
            this.future = requireNonNull(future, "future is null");
            checkArgument(maxLength > 0, "maxLength must be greater than zero");
            this.maxLength = maxLength;
        }

        @Override
        public synchronized void onHeaders(Response response)
        {
            long length = response.getHeaders().getLongField(HttpHeader.CONTENT_LENGTH.asString());
            if (length > maxLength) {
                response.abort(new ResponseTooLargeException());
            }
        }

        @Override
        public synchronized void onContent(Response response, ByteBuffer content)
        {
            int length = content.remaining();
            size += length;
            if (size > maxLength) {
                response.abort(new ResponseTooLargeException());
                return;
            }

            while (length > 0) {
                if (currentBufferPosition >= currentBuffer.length) {
                    allocateCurrentBuffer();
                }
                int readLength = min(length, currentBuffer.length - currentBufferPosition);
                content.get(currentBuffer, currentBufferPosition, readLength);
                length -= readLength;
                currentBufferPosition += readLength;
            }
        }

        @Override
        public synchronized void onComplete(Result result)
        {
            Throwable throwable = result.getFailure();
            if (throwable != null) {
                future.failed(throwable);
            }
            else {
                currentBuffer = new byte[0];
                currentBufferPosition = 0;
                future.completed(result.getResponse(), new GatheringByteArrayInputStream(buffers, size));
                buffers = new ArrayList<>();
                size = 0;
            }
        }

        private synchronized void allocateCurrentBuffer()
        {
            checkState(currentBufferPosition >= currentBuffer.length, "there is still remaining space in currentBuffer");

            currentBuffer = new byte[(int) min(BUFFER_MAX_BYTES, max(2 * currentBuffer.length, BUFFER_MIN_BYTES))];
            buffers.add(currentBuffer);
            currentBufferPosition = 0;
        }
    }

    /*
     * This class is needed because jmxutils only fetches a nested instance object once and holds on to it forever.
     * todo remove this when https://github.com/martint/jmxutils/issues/26 is implemented
     */
    @ThreadSafe
    public static class CachedDistribution
    {
        private final Supplier<Distribution> distributionSupplier;

        @GuardedBy("this")
        private Distribution distribution;
        @GuardedBy("this")
        private long lastUpdate = System.nanoTime();

        public CachedDistribution(Supplier<Distribution> distributionSupplier)
        {
            this.distributionSupplier = distributionSupplier;
        }

        public synchronized Distribution getDistribution()
        {
            // refresh stats only once a second
            if (NANOSECONDS.toMillis(System.nanoTime() - lastUpdate) > 1000) {
                this.distribution = distributionSupplier.get();
                this.lastUpdate = System.nanoTime();
            }
            return distribution;
        }

        @Managed
        public double getMaxError()
        {
            return getDistribution().getMaxError();
        }

        @Managed
        public double getCount()
        {
            return getDistribution().getCount();
        }

        @Managed
        public double getTotal()
        {
            return getDistribution().getTotal();
        }

        @Managed
        public long getP01()
        {
            return getDistribution().getP01();
        }

        @Managed
        public long getP05()
        {
            return getDistribution().getP05();
        }

        @Managed
        public long getP10()
        {
            return getDistribution().getP10();
        }

        @Managed
        public long getP25()
        {
            return getDistribution().getP25();
        }

        @Managed
        public long getP50()
        {
            return getDistribution().getP50();
        }

        @Managed
        public long getP75()
        {
            return getDistribution().getP75();
        }

        @Managed
        public long getP90()
        {
            return getDistribution().getP90();
        }

        @Managed
        public long getP95()
        {
            return getDistribution().getP95();
        }

        @Managed
        public long getP99()
        {
            return getDistribution().getP99();
        }

        @Managed
        public long getMin()
        {
            return getDistribution().getMin();
        }

        @Managed
        public long getMax()
        {
            return getDistribution().getMax();
        }

        @Managed
        public Map<Double, Long> getPercentiles()
        {
            return getDistribution().getPercentiles();
        }
    }

    private static class JettyRequestListener
    {
        enum State
        {
            CREATED, SENDING_REQUEST, AWAITING_RESPONSE, READING_RESPONSE, FINISHED
        }

        private final AtomicReference<State> state = new AtomicReference<>(State.CREATED);

        private final URI uri;
        private final long created = System.nanoTime();
        private final AtomicLong requestStarted = new AtomicLong();
        private final AtomicLong requestFinished = new AtomicLong();
        private final AtomicLong responseStarted = new AtomicLong();
        private final AtomicLong responseFinished = new AtomicLong();

        public JettyRequestListener(URI uri)
        {
            this.uri = uri;
        }

        public URI getUri()
        {
            return uri;
        }

        public State getState()
        {
            return state.get();
        }

        public long getCreated()
        {
            return created;
        }

        public long getRequestStarted()
        {
            return requestStarted.get();
        }

        public long getRequestFinished()
        {
            return requestFinished.get();
        }

        public long getResponseStarted()
        {
            return responseStarted.get();
        }

        public long getResponseFinished()
        {
            return responseFinished.get();
        }

        public void onRequestBegin()
        {
            changeState(State.SENDING_REQUEST);

            long now = System.nanoTime();
            requestStarted.compareAndSet(0, now);
        }

        public void onRequestEnd()
        {
            changeState(State.AWAITING_RESPONSE);

            long now = System.nanoTime();
            requestStarted.compareAndSet(0, now);
            requestFinished.compareAndSet(0, now);
        }

        private void onResponseBegin()
        {
            changeState(State.READING_RESPONSE);

            long now = System.nanoTime();
            requestStarted.compareAndSet(0, now);
            requestFinished.compareAndSet(0, now);
            responseStarted.compareAndSet(0, now);
        }

        private void onFinish()
        {
            changeState(State.FINISHED);

            long now = System.nanoTime();
            requestStarted.compareAndSet(0, now);
            requestFinished.compareAndSet(0, now);
            responseStarted.compareAndSet(0, now);
            responseFinished.compareAndSet(0, now);
        }

        private synchronized void changeState(State newState)
        {
            if (state.get().ordinal() < newState.ordinal()) {
                state.set(newState);
            }
        }
    }

    private static class ConnectionPoolDistribution
            extends CachedDistribution
    {
        interface Processor
        {
            void process(Distribution distribution, DuplexConnectionPool pool);
        }

        public ConnectionPoolDistribution(HttpClient httpClient, Processor processor)
        {
            super(() -> {
                Distribution distribution = new Distribution();
                httpClient.getDestinations().stream()
                        .filter(PoolingHttpDestination.class::isInstance)
                        .map(destination -> (PoolingHttpDestination<?>) destination)
                        .map(PoolingHttpDestination::getConnectionPool)
                        .filter(pool -> pool != null)
                        .forEach(pool -> processor.process(distribution, pool));
                return distribution;
            });
        }
    }

    private static class DestinationDistribution
            extends CachedDistribution
    {
        interface Processor
        {
            void process(Distribution distribution, PoolingHttpDestination<?> destination);
        }

        public DestinationDistribution(HttpClient httpClient, Processor processor)
        {
            super(() -> {
                Distribution distribution = new Distribution();
                httpClient.getDestinations().stream()
                        .filter(PoolingHttpDestination.class::isInstance)
                        .map(destination -> (PoolingHttpDestination<?>) destination)
                        .forEach(destination -> processor.process(distribution, destination));
                return distribution;
            });
        }
    }

    private static class RequestDistribution
            extends CachedDistribution
    {
        interface Processor
        {
            void process(Distribution distribution, JettyRequestListener listener, long now);
        }

        public RequestDistribution(HttpClient httpClient, Processor processor)
        {
            super(() -> {
                long now = System.nanoTime();
                Distribution distribution = new Distribution();
                httpClient.getDestinations().stream()
                        .filter(PoolingHttpDestination.class::isInstance)
                        .map(destination -> (PoolingHttpDestination<?>) destination)
                        .map(JettyHttpClient::getRequestListenersForDestination)
                        .flatMap(List::stream)
                        .forEach(listener -> processor.process(distribution, listener, now));
                return distribution;
            });
        }
    }

    // By wrapping HttpClient, we are able to substitute the underlying AuthenticationStore
    // with a more efficient one.
    private static class SpnegoHttpClient
            extends HttpClient
    {
        private final AuthenticationStore authenticationStore;
        private final SpnegoAuthentication spnego;

        public SpnegoHttpClient(KerberosConfig kerberosConfig, HttpClientConfig config, HttpClientTransport transport, SslContextFactory sslContextFactory)
        {
            super(transport, sslContextFactory);

            spnego = new SpnegoAuthentication(
                    kerberosConfig.getKeytab(),
                    kerberosConfig.getConfig(),
                    kerberosConfig.getCredentialCache(),
                    config.getKerberosPrincipal(),
                    config.getKerberosRemoteServiceName(),
                    kerberosConfig.isUseCanonicalHostname());

            authenticationStore = new SpnegoAuthenticationStore(spnego);
        }

        @Override
        public AuthenticationStore getAuthenticationStore()
        {
            return authenticationStore;
        }

        @Override
        protected void doStop()
                throws Exception
        {
            authenticationStore.clearAuthenticationResults();
            super.doStop();
        }
    }
}
