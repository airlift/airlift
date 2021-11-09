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
import io.airlift.security.pem.PemReader;
import io.airlift.units.Duration;
import org.eclipse.jetty.client.DuplexConnectionPool;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.HttpClientTransport;
import org.eclipse.jetty.client.HttpExchange;
import org.eclipse.jetty.client.HttpProxy;
import org.eclipse.jetty.client.HttpRequest;
import org.eclipse.jetty.client.Origin.Address;
import org.eclipse.jetty.client.PoolingHttpDestination;
import org.eclipse.jetty.client.Socks4Proxy;
import org.eclipse.jetty.client.api.ContentProvider;
import org.eclipse.jetty.client.api.Destination;
import org.eclipse.jetty.client.api.Response;
import org.eclipse.jetty.client.http.HttpClientTransportOverHTTP;
import org.eclipse.jetty.client.http.HttpConnectionOverHTTP;
import org.eclipse.jetty.client.util.BytesContentProvider;
import org.eclipse.jetty.client.util.InputStreamResponseListener;
import org.eclipse.jetty.client.util.PathContentProvider;
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

import javax.annotation.PreDestroy;
import javax.net.ssl.SNIHostName;
import javax.net.ssl.SNIServerName;
import javax.net.ssl.SSLEngine;
import javax.security.auth.x500.X500Principal;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.LocalDate;
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

import static com.google.common.base.MoreObjects.firstNonNull;
import static com.google.common.base.MoreObjects.toStringHelper;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Throwables.throwIfUnchecked;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.net.InetAddresses.isInetAddress;
import static io.airlift.http.client.jetty.AuthorizationPreservingHttpClient.setPreserveAuthorization;
import static io.airlift.node.AddressToHostname.tryDecodeHostnameToAddress;
import static io.airlift.security.cert.CertificateBuilder.certificateBuilder;
import static java.lang.Math.max;
import static java.lang.Math.toIntExact;
import static java.lang.String.format;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.time.temporal.ChronoUnit.YEARS;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

public class JettyHttpClient
        implements io.airlift.http.client.HttpClient
{
    private static final String STATS_KEY = "airlift_stats";
    private static final long SWEEP_PERIOD_MILLIS = 5000;

    private static final AtomicLong NAME_COUNTER = new AtomicLong();

    private final HttpClient httpClient;
    private final long maxContentLength;
    private final long requestTimeoutMillis;
    private final long idleTimeoutMillis;
    private final boolean recordRequestComplete;
    private final boolean logEnabled;
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
    private final JettyClientDiagnostics clientDiagnostics;

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
        this(name, config, ImmutableList.of());
    }

    public JettyHttpClient(
            String name,
            HttpClientConfig config,
            Iterable<? extends HttpRequestFilter> requestFilters)
    {
        this(name, config, requestFilters, Optional.empty(), Optional.empty());
    }

    public JettyHttpClient(
            String name,
            HttpClientConfig config,
            Iterable<? extends HttpRequestFilter> requestFilters,
            Optional<String> environment,
            Optional<SslContextFactory.Client> maybeSslContextFactory)
    {
        this.name = requireNonNull(name, "name is null");

        requireNonNull(config, "config is null");
        requireNonNull(requestFilters, "requestFilters is null");

        maxContentLength = config.getMaxContentLength().toBytes();
        requestTimeoutMillis = config.getRequestTimeout().toMillis();
        idleTimeoutMillis = config.getIdleTimeout().toMillis();
        recordRequestComplete = config.getRecordRequestComplete();

        creationLocation.fillInStackTrace();

        SslContextFactory.Client sslContextFactory = maybeSslContextFactory.orElseGet(() -> getSslContextFactory(config, environment));

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

        httpClient = new AuthorizationPreservingHttpClient(transport, sslContextFactory);

        // request and response buffer size
        httpClient.setRequestBufferSize(toIntExact(config.getRequestBufferSize().toBytes()));
        httpClient.setResponseBufferSize(toIntExact(config.getResponseBufferSize().toBytes()));

        httpClient.setMaxConnectionsPerDestination(config.getMaxConnectionsPerServer());
        httpClient.setMaxRequestsQueuedPerDestination(config.getMaxRequestsQueuedPerDestination());

        // disable cookies
        httpClient.setCookieStore(new HttpCookieStore.Empty());

        // remove default user agent
        httpClient.setUserAgentField(null);

        // timeouts
        httpClient.setIdleTimeout(idleTimeoutMillis);
        httpClient.setConnectTimeout(config.getConnectTimeout().toMillis());
        httpClient.setAddressResolutionTimeout(config.getConnectTimeout().toMillis());

        httpClient.setConnectBlocking(config.isConnectBlocking());

        HostAndPort socksProxy = config.getSocksProxy();
        if (socksProxy != null) {
            httpClient.getProxyConfiguration().getProxies().add(new Socks4Proxy(socksProxy.getHost(), socksProxy.getPortOrDefault(1080)));
        }
        HostAndPort httpProxy = config.getHttpProxy();
        if (httpProxy != null) {
            httpClient.getProxyConfiguration().getProxies().add(new HttpProxy(new Address(httpProxy.getHost(), httpProxy.getPortOrDefault(8080)), config.isSecureProxy()));
        }

        httpClient.setByteBufferPool(new MappedByteBufferPool());
        QueuedThreadPool queuedThreadPool = createExecutor(name, config.getMinThreads(), config.getMaxThreads());
        httpClient.setExecutor(queuedThreadPool);
        // add queuedThreadPool as a managed bean to get its state in the client dumps
        httpClient.addBean(queuedThreadPool, true);
        httpClient.setScheduler(createScheduler(name, config.getTimeoutConcurrency(), config.getTimeoutThreads()));

        JettyAsyncSocketAddressResolver resolver = new JettyAsyncSocketAddressResolver(
                httpClient.getExecutor(),
                httpClient.getScheduler(),
                config.getConnectTimeout().toMillis());
        httpClient.setSocketAddressResolver((host, port, promise) -> {
            Optional<InetAddress> inetAddress = tryDecodeHostnameToAddress(host);
            if (inetAddress.isPresent()) {
                promise.succeeded(ImmutableList.of(new InetSocketAddress(inetAddress.get(), port)));
                return;
            }
            resolver.resolve(host, port, promise);
        });

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
        this.logEnabled = config.isLogEnabled();
        if (logEnabled) {
            String logFilePath = Paths.get(config.getLogPath(), format("%s-http-client.log", name)).toAbsolutePath().toString();
            requestLogger = new DefaultHttpClientLogger(
                    logFilePath,
                    config.getLogHistory(),
                    config.getLogQueueSize(),
                    config.getLogBufferSize(),
                    config.getLogFlushInterval(),
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

        this.clientDiagnostics = new JettyClientDiagnostics();

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

    private static SslContextFactory.Client getSslContextFactory(HttpClientConfig config, Optional<String> environment)
    {
        SslContextFactory.Client sslContextFactory = new SslContextFactory.Client();
        sslContextFactory.setSNIProvider(JettyHttpClient::getSniServerNames);
        sslContextFactory.setEndpointIdentificationAlgorithm(config.isVerifyHostname() ? "HTTPS" : null);

        String keyStorePassword = firstNonNull(config.getKeyStorePassword(), "");
        KeyStore keyStore = null;
        if (config.getKeyStorePath() != null) {
            keyStore = loadKeyStore(config.getKeyStorePath(), config.getKeyStorePassword());
            sslContextFactory.setKeyStore(keyStore);
            sslContextFactory.setKeyStorePassword(keyStorePassword);
        }

        if (config.getTrustStorePath() != null || config.getAutomaticHttpsSharedSecret() != null) {
            KeyStore trustStore = loadTrustStore(config.getTrustStorePath(), config.getTrustStorePassword());
            if (config.getAutomaticHttpsSharedSecret() != null) {
                addAutomaticTrust(config.getAutomaticHttpsSharedSecret(), trustStore, environment
                        .orElseThrow(() -> new IllegalArgumentException("Environment must be provided when automatic HTTPS is enabled")));
            }
            sslContextFactory.setTrustStore(trustStore);
            sslContextFactory.setTrustStorePassword("");
        }
        else if (keyStore != null) {
            // Backwards compatibility for with Jetty's internal behavior
            sslContextFactory.setTrustStore(keyStore);
            sslContextFactory.setTrustStorePassword(keyStorePassword);
        }

        sslContextFactory.setSecureRandomAlgorithm(config.getSecureRandomAlgorithm());
        List<String> includedCipherSuites = config.getHttpsIncludedCipherSuites();
        List<String> excludedCipherSuites = config.getHttpsExcludedCipherSuites();
        sslContextFactory.setIncludeCipherSuites(includedCipherSuites.toArray(new String[0]));
        sslContextFactory.setExcludeCipherSuites(excludedCipherSuites.toArray(new String[0]));

        return sslContextFactory;
    }

    private static List<SNIServerName> getSniServerNames(SSLEngine sslEngine, List<SNIServerName> serverNames)
    {
        // work around the JDK TLS implementation not allowing single label domains
        if (serverNames.isEmpty()) {
            String host = sslEngine.getPeerHost();
            if (host != null && !isInetAddress(host) && !host.contains(".")) {
                try {
                    return List.of(new SNIHostName(host));
                }
                catch (IllegalArgumentException ignored) {
                }
            }
        }
        return serverNames;
    }

    private static KeyStore loadKeyStore(String keystorePath, String keystorePassword)
    {
        requireNonNull(keystorePath, "keystorePath is null");
        try {
            File keyStoreFile = new File(keystorePath);
            if (PemReader.isPem(keyStoreFile)) {
                return PemReader.loadKeyStore(keyStoreFile, keyStoreFile, Optional.ofNullable(keystorePassword), true);
            }
        }
        catch (IOException | GeneralSecurityException e) {
            throw new IllegalArgumentException("Error loading PEM key store: " + keystorePath, e);
        }

        try (InputStream in = new FileInputStream(keystorePath)) {
            KeyStore keyStore = KeyStore.getInstance("JKS");
            keyStore.load(in, keystorePassword.toCharArray());
            return keyStore;
        }
        catch (IOException | GeneralSecurityException e) {
            throw new IllegalArgumentException("Error loading Java key store: " + keystorePath, e);
        }
    }

    private static KeyStore loadTrustStore(String truststorePath, String truststorePassword)
    {
        if (truststorePath == null) {
            try {
                KeyStore keyStore = KeyStore.getInstance("JKS");
                keyStore.load(null, new char[0]);
                return keyStore;
            }
            catch (GeneralSecurityException | IOException e) {
                throw new RuntimeException(e);
            }
        }

        try {
            File keyStoreFile = new File(truststorePath);
            if (PemReader.isPem(keyStoreFile)) {
                return PemReader.loadTrustStore(keyStoreFile);
            }
        }
        catch (IOException | GeneralSecurityException e) {
            throw new IllegalArgumentException("Error loading PEM trust store: " + truststorePath, e);
        }

        try (InputStream in = new FileInputStream(truststorePath)) {
            KeyStore keyStore = KeyStore.getInstance("JKS");
            keyStore.load(in, truststorePassword == null ? null : truststorePassword.toCharArray());
            return keyStore;
        }
        catch (IOException | GeneralSecurityException e) {
            throw new IllegalArgumentException("Error loading Java trust store: " + truststorePath, e);
        }
    }

    private static void addAutomaticTrust(String sharedSecret, KeyStore keyStore, String commonName)
    {
        try {
            byte[] seed = sharedSecret.getBytes(UTF_8);
            SecureRandom secureRandom = SecureRandom.getInstance("SHA1PRNG");
            secureRandom.setSeed(seed);

            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048, secureRandom);
            KeyPair keyPair = generator.generateKeyPair();

            X500Principal subject = new X500Principal("CN=" + commonName);
            LocalDate notBefore = LocalDate.now();
            LocalDate notAfter = notBefore.plus(10, YEARS);
            X509Certificate certificateServer = certificateBuilder()
                    .setKeyPair(keyPair)
                    .setSerialNumber(System.currentTimeMillis())
                    .setIssuer(subject)
                    .setNotBefore(notBefore)
                    .setNotAfter(notAfter)
                    .setSubject(subject)
                    .buildSelfSigned();

            keyStore.setCertificateEntry(commonName, certificateServer);
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
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
        JettyRequestListener requestListener = new JettyRequestListener(request.getUri());
        HttpRequest jettyRequest = buildJettyRequest(request, requestListener);
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

        long requestTimestamp = System.currentTimeMillis();
        RequestInfo requestInfo = RequestInfo.from(jettyRequest, requestTimestamp);
        if (logEnabled) {
            addLoggingListener(jettyRequest, requestTimestamp);
        }

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
            if (jettyResponse != null) {
                try {
                    jettyResponse.getInputStream().close();
                }
                catch (IOException ignored) {
                    // ignore errors closing the stream
                }
            }
            if (recordRequestComplete) {
                recordRequestComplete(stats, request, requestStart, jettyResponse, responseStart);
            }
        }
        return value;
    }

    @Override
    public <T, E extends Exception> HttpResponseFuture<T> executeAsync(Request request, ResponseHandler<T, E> responseHandler)
    {
        requireNonNull(request, "request is null");
        requireNonNull(responseHandler, "responseHandler is null");

        request = applyRequestFilters(request);

        HttpRequest jettyRequest = buildJettyRequest(request, new JettyRequestListener(request.getUri()));

        JettyResponseFuture<T, E> future = new JettyResponseFuture<>(request, jettyRequest, responseHandler, stats, recordRequestComplete);

        BufferingResponseListener listener = new BufferingResponseListener(future, Ints.saturatedCast(maxContentLength));

        long requestTimestamp = System.currentTimeMillis();

        if (logEnabled) {
            addLoggingListener(jettyRequest, requestTimestamp);
        }

        try {
            jettyRequest.send(listener);
        }
        catch (RuntimeException e) {
            if (!(e instanceof RejectedExecutionException)) {
                e = new RejectedExecutionException(e);
            }
            // normally this is a rejected execution exception because the client has been closed
            future.failed(e);
            requestLogger.log(RequestInfo.from(jettyRequest, requestTimestamp), ResponseInfo.failed(Optional.empty(), Optional.of(e)));
        }
        return future;
    }

    private void addLoggingListener(HttpRequest jettyRequest, long requestTimestamp)
    {
        HttpClientLoggingListener loggingListener = new HttpClientLoggingListener(jettyRequest, requestTimestamp, requestLogger);
        jettyRequest.listener(loggingListener);
        jettyRequest.onResponseBegin(loggingListener);
        jettyRequest.onComplete(loggingListener);
    }

    private Request applyRequestFilters(Request request)
    {
        for (HttpRequestFilter requestFilter : requestFilters) {
            request = requestFilter.filterRequest(request);
        }
        return request;
    }

    private HttpRequest buildJettyRequest(Request finalRequest, JettyRequestListener listener)
    {
        HttpRequest jettyRequest = (HttpRequest) httpClient.newRequest(finalRequest.getUri());
        jettyRequest.onRequestBegin(request -> listener.onRequestBegin());
        jettyRequest.onRequestSuccess(request -> listener.onRequestEnd());
        jettyRequest.onResponseBegin(response -> listener.onResponseBegin());
        jettyRequest.onComplete(result -> listener.onFinish());
        jettyRequest.onComplete(result -> {
            if (result.isFailed() && result.getFailure() instanceof TimeoutException) {
                clientDiagnostics.logDiagnosticsInfo(httpClient);
            }
        });

        jettyRequest.attribute(STATS_KEY, listener);

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

        jettyRequest.followRedirects(finalRequest.isFollowRedirects());

        setPreserveAuthorization(jettyRequest, finalRequest.isPreserveAuthorizationOnRedirect());

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
                .map(request -> request.getAttributes().get(STATS_KEY))
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

    @PreDestroy
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
