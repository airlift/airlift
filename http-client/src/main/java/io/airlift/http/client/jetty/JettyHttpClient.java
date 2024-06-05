package io.airlift.http.client.jetty;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ListMultimap;
import com.google.common.net.HostAndPort;
import com.google.common.primitives.Ints;
import io.airlift.http.client.BodyGenerator;
import io.airlift.http.client.ByteBufferBodyGenerator;
import io.airlift.http.client.FileBodyGenerator;
import io.airlift.http.client.HeaderName;
import io.airlift.http.client.HttpClientConfig;
import io.airlift.http.client.HttpRequestFilter;
import io.airlift.http.client.HttpStatusListener;
import io.airlift.http.client.Request;
import io.airlift.http.client.RequestStats;
import io.airlift.http.client.ResponseHandler;
import io.airlift.http.client.StaticBodyGenerator;
import io.airlift.http.client.StreamingBodyGenerator;
import io.airlift.http.client.StreamingResponse;
import io.airlift.http.client.jetty.HttpClientLogger.RequestInfo;
import io.airlift.http.client.jetty.HttpClientLogger.ResponseInfo;
import io.airlift.security.pem.PemReader;
import io.airlift.units.DataSize;
import io.airlift.units.Duration;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.TracerProvider;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.semconv.ExceptionAttributes;
import io.opentelemetry.semconv.HttpAttributes;
import io.opentelemetry.semconv.NetworkAttributes;
import io.opentelemetry.semconv.ServerAttributes;
import io.opentelemetry.semconv.UrlAttributes;
import io.opentelemetry.semconv.incubating.HttpIncubatingAttributes;
import jakarta.annotation.PreDestroy;
import jdk.net.ExtendedSocketOptions;
import org.eclipse.jetty.client.AbstractConnectionPool;
import org.eclipse.jetty.client.ByteBufferRequestContent;
import org.eclipse.jetty.client.BytesRequestContent;
import org.eclipse.jetty.client.Destination;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.HttpClientTransport;
import org.eclipse.jetty.client.HttpProxy;
import org.eclipse.jetty.client.InputStreamRequestContent;
import org.eclipse.jetty.client.InputStreamResponseListener;
import org.eclipse.jetty.client.Origin.Address;
import org.eclipse.jetty.client.PathRequestContent;
import org.eclipse.jetty.client.Response;
import org.eclipse.jetty.client.Result;
import org.eclipse.jetty.client.Socks4Proxy;
import org.eclipse.jetty.client.transport.HttpClientConnectionFactory;
import org.eclipse.jetty.client.transport.HttpClientTransportDynamic;
import org.eclipse.jetty.client.transport.HttpDestination;
import org.eclipse.jetty.client.transport.HttpExchange;
import org.eclipse.jetty.client.transport.HttpRequest;
import org.eclipse.jetty.client.transport.internal.HttpConnectionOverHTTP;
import org.eclipse.jetty.http.HttpCookieStore;
import org.eclipse.jetty.http.HttpVersion;
import org.eclipse.jetty.http2.client.HTTP2Client;
import org.eclipse.jetty.http2.client.transport.ClientConnectionFactoryOverHTTP2;
import org.eclipse.jetty.io.ArrayByteBufferPool;
import org.eclipse.jetty.io.ClientConnectionFactory;
import org.eclipse.jetty.io.ClientConnector;
import org.eclipse.jetty.io.ConnectionStatistics;
import org.eclipse.jetty.util.component.LifeCycle;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.util.thread.MonitoredQueuedThreadPool;
import org.eclipse.jetty.util.thread.ScheduledExecutorScheduler;
import org.eclipse.jetty.util.thread.Scheduler;
import org.weakref.jmx.Flatten;
import org.weakref.jmx.Managed;
import org.weakref.jmx.Nested;

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
import java.net.StandardSocketOptions;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.NetworkChannel;
import java.nio.channels.SelectableChannel;
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
import static io.airlift.http.client.ResponseHandlerUtils.propagate;
import static io.airlift.http.client.jetty.AuthorizationPreservingHttpClient.setPreserveAuthorization;
import static io.airlift.node.AddressToHostname.tryDecodeHostnameToAddress;
import static io.airlift.security.cert.CertificateBuilder.certificateBuilder;
import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static java.lang.Math.max;
import static java.lang.Math.toIntExact;
import static java.lang.String.format;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.time.temporal.ChronoUnit.YEARS;
import static java.util.Locale.ENGLISH;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.eclipse.jetty.client.ConnectionPoolAccessor.getActiveConnections;
import static org.eclipse.jetty.client.ConnectionPoolAccessor.getIdleConnections;
import static org.eclipse.jetty.client.HttpClient.normalizePort;

public class JettyHttpClient
        implements io.airlift.http.client.HttpClient
{
    private static final String STATS_KEY = "airlift_stats";

    private static final AtomicLong NAME_COUNTER = new AtomicLong();

    private static final OpenTelemetry NOOP_OPEN_TELEMETRY = OpenTelemetry.noop();
    private static final Tracer NOOP_TRACER = TracerProvider.noop().get("noop");

    private static final AttributeKey<String> CLIENT_NAME = stringKey("airlift.http.client_name");

    private final HttpClient httpClient;
    private final DataSize maxContentLength;
    private final Duration requestTimeout;
    private final Duration idleTimeout;
    private final long destinationIdleTimeoutMillis;
    private final boolean recordRequestComplete;
    private final boolean logEnabled;
    private final MonitoredQueuedThreadPoolMBean monitoredQueuedThreadPoolMBean;
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
    private final List<HttpStatusListener> httpStatusListeners;
    private final Exception creationLocation = new Exception();
    private final String name;
    private final TextMapPropagator propagator;
    private final Tracer tracer;

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
            Iterable<? extends HttpStatusListener> httpStatusListeners)
    {
        this(name, config, requestFilters, NOOP_OPEN_TELEMETRY, NOOP_TRACER, Optional.empty(), Optional.empty(), httpStatusListeners);
    }

    public JettyHttpClient(
            String name,
            HttpClientConfig config,
            Iterable<? extends HttpRequestFilter> requestFilters,
            Optional<String> environment,
            Optional<SslContextFactory.Client> maybeSslContextFactory)
    {
        this(name, config, requestFilters, NOOP_OPEN_TELEMETRY, NOOP_TRACER, environment, maybeSslContextFactory);
    }

    public JettyHttpClient(
            String name,
            HttpClientConfig config,
            Iterable<? extends HttpRequestFilter> requestFilters,
            OpenTelemetry openTelemetry,
            Tracer tracer,
            Optional<String> environment,
            Optional<SslContextFactory.Client> maybeSslContextFactory)
    {
        this(name, config, requestFilters, openTelemetry, tracer, environment, maybeSslContextFactory, ImmutableList.of());
    }

    public JettyHttpClient(
            String name,
            HttpClientConfig config,
            Iterable<? extends HttpRequestFilter> requestFilters,
            OpenTelemetry openTelemetry,
            Tracer tracer,
            Optional<String> environment,
            Optional<SslContextFactory.Client> maybeSslContextFactory,
            Iterable<? extends HttpStatusListener> httpStatusListeners)
    {
        this.name = requireNonNull(name, "name is null");
        this.propagator = openTelemetry.getPropagators().getTextMapPropagator();
        this.tracer = requireNonNull(tracer, "tracer is null");

        requireNonNull(config, "config is null");
        requireNonNull(requestFilters, "requestFilters is null");
        requireNonNull(httpStatusListeners, "httpStatusListeners is null");

        maxContentLength = config.getMaxContentLength();
        requestTimeout = config.getRequestTimeout();
        idleTimeout = config.getIdleTimeout();
        destinationIdleTimeoutMillis = config.getDestinationIdleTimeout().toMillis();
        recordRequestComplete = config.getRecordRequestComplete();

        creationLocation.fillInStackTrace();

        SslContextFactory.Client sslContextFactory = maybeSslContextFactory.orElseGet(() -> getSslContextFactory(config, environment));

        ClientConnector connector = new ClientConnector()
        {
            @Override
            protected void configure(SelectableChannel selectable)
                    throws IOException
            {
                super.configure(selectable);
                if (config.getTcpKeepAliveIdleTime().isPresent()) {
                    setKeepAlive(selectable, config.getTcpKeepAliveIdleTime().get());
                }
            }
        };

        connector.setSelectors(config.getSelectorCount());
        connector.setSslContextFactory(sslContextFactory);

        httpClient = new AuthorizationPreservingHttpClient(getClientTransport(connector, config));

        // request and response buffer size
        httpClient.setRequestBufferSize(toIntExact(config.getRequestBufferSize().toBytes()));
        httpClient.setResponseBufferSize(toIntExact(config.getResponseBufferSize().toBytes()));

        httpClient.setMaxConnectionsPerDestination(config.getMaxConnectionsPerServer());
        httpClient.setMaxRequestsQueuedPerDestination(config.getMaxRequestsQueuedPerDestination());
        httpClient.setDestinationIdleTimeout(destinationIdleTimeoutMillis);

        // disable cookies
        httpClient.setHttpCookieStore(new HttpCookieStore.Empty());

        // remove default user agent
        httpClient.setUserAgentField(null);

        // timeouts
        httpClient.setIdleTimeout(idleTimeout.toMillis());
        httpClient.setConnectTimeout(config.getConnectTimeout().toMillis());
        httpClient.setAddressResolutionTimeout(config.getConnectTimeout().toMillis());

        httpClient.setConnectBlocking(config.isConnectBlocking());

        HostAndPort socksProxy = config.getSocksProxy();
        if (socksProxy != null) {
            httpClient.getProxyConfiguration().addProxy(new Socks4Proxy(socksProxy.getHost(), socksProxy.getPortOrDefault(1080)));
        }
        HostAndPort httpProxy = config.getHttpProxy();
        if (httpProxy != null) {
            httpClient.getProxyConfiguration().addProxy(new HttpProxy(new Address(httpProxy.getHost(), httpProxy.getPortOrDefault(8080)), config.isSecureProxy()));
        }

        httpClient.setByteBufferPool(new ArrayByteBufferPool());
        httpClient.setExecutor(createExecutor(name, config.getMinThreads(), config.getMaxThreads()));
        httpClient.setScheduler(createScheduler(name, config.getTimeoutConcurrency(), config.getTimeoutThreads()));
        httpClient.setStrictEventOrdering(config.isStrictEventOrdering());

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
        this.httpStatusListeners = ImmutableList.copyOf(httpStatusListeners);

        this.monitoredQueuedThreadPoolMBean = new MonitoredQueuedThreadPoolMBean((MonitoredQueuedThreadPool) httpClient.getExecutor());

        this.activeConnectionsPerDestination = new ConnectionPoolDistribution(httpClient,
                (distribution, connectionPool) -> distribution.add(getActiveConnections(connectionPool).size()));

        this.idleConnectionsPerDestination = new ConnectionPoolDistribution(httpClient,
                (distribution, connectionPool) -> distribution.add(getIdleConnections(connectionPool).size()));

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

    private HttpClientTransport getClientTransport(ClientConnector connector, HttpClientConfig config)
    {
        ImmutableList.Builder<ClientConnectionFactory.Info> protocols = ImmutableList.builder();
        if (config.isHttp2Enabled()) {
            HTTP2Client client = new HTTP2Client(connector);
            client.setInitialSessionRecvWindow(toIntExact(config.getHttp2InitialSessionReceiveWindowSize().toBytes()));
            client.setInitialStreamRecvWindow(toIntExact(config.getHttp2InitialStreamReceiveWindowSize().toBytes()));
            client.setInputBufferSize(toIntExact(config.getHttp2InputBufferSize().toBytes()));
            client.setStreamIdleTimeout(idleTimeout.toMillis());
            client.setSelectors(config.getSelectorCount());
            protocols.add(new ClientConnectionFactoryOverHTTP2.HTTP2(client));
        }

        protocols.add(HttpClientConnectionFactory.HTTP11);

        // The order of the protocols indicates the client's preference.
        // The first is the most preferred, the last is the least preferred, but
        // the protocol version to use can be explicitly specified in the request.
        return new HttpClientTransportDynamic(connector, protocols.build().toArray(new ClientConnectionFactory.Info[0]));
    }

    private static void setKeepAlive(SelectableChannel selectable, Duration tcpKeepAliveIdleTime)
            throws IOException
    {
        if (selectable instanceof NetworkChannel channel) {
            channel.setOption(StandardSocketOptions.SO_KEEPALIVE, true);
            channel.setOption(ExtendedSocketOptions.TCP_KEEPIDLE, toIntExact(tcpKeepAliveIdleTime.roundTo(SECONDS)));
        }
        else {
            throw new IOException("Not a NetworkChannel. Cannot enable keep alive for %s".formatted(selectable.getClass()));
        }
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

    private static MonitoredQueuedThreadPool createExecutor(String name, int minThreads, int maxThreads)
    {
        try {
            MonitoredQueuedThreadPool pool = new MonitoredQueuedThreadPool(maxThreads, minThreads, 60000, null);
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
        request = applyRequestFilters(request);

        Span span = startSpan(request);
        request = injectTracing(request, span);

        try {
            return doExecute(request, responseHandler, span);
        }
        catch (Throwable t) {
            span.setStatus(StatusCode.ERROR, t.getMessage());
            span.recordException(t, Attributes.of(ExceptionAttributes.EXCEPTION_ESCAPED, true));
            throw t;
        }
        finally {
            span.end();
        }
    }

    @Override
    public StreamingResponse executeStreaming(Request request)
    {
        request = applyRequestFilters(request);

        Span span = startSpan(request);
        request = injectTracing(request, span);

        ExceptionHandler<StreamingResponse, RuntimeException> exceptionHandler = (r, exception) -> {
            try {
                span.setStatus(StatusCode.ERROR, exception.getMessage());
                span.recordException(exception, Attributes.of(ExceptionAttributes.EXCEPTION_ESCAPED, true));

                throw propagate(r, exception);
            }
            finally {
                span.end();
            }
        };

        return switch (internalExecute(request, exceptionHandler, span)) {
            case InternalExceptionResponse<StreamingResponse> response -> response.exceptionResponse;
            case InternalStandardResponse<StreamingResponse> response -> new StreamingResponse()
            {
                @Override
                public io.airlift.http.client.HttpVersion getHttpVersion()
                {
                    return response.jettyResponse.getHttpVersion();
                }

                @Override
                public int getStatusCode()
                {
                    return response.jettyResponse.getStatusCode();
                }

                @Override
                public ListMultimap<HeaderName, String> getHeaders()
                {
                    return response.jettyResponse.getHeaders();
                }

                @Override
                public long getBytesRead()
                {
                    return response.jettyResponse.getBytesRead();
                }

                @Override
                public InputStream getInputStream()
                {
                    return response.jettyResponse.getInputStream();
                }

                @Override
                public void close()
                {
                    try {
                        response.completionHandler.run();
                    }
                    finally {
                        span.end();
                    }
                }
            };
        };
    }

    public <T, E extends Exception> T doExecute(Request request, ResponseHandler<T, E> responseHandler, Span span)
            throws E
    {
        return switch (internalExecute(request, responseHandler::handleException, span)) {
            case InternalExceptionResponse<T> response -> response.exceptionResponse;
            case InternalStandardResponse<T> response -> {
                try {
                    yield responseHandler.handle(request, response.jettyResponse);
                }
                finally {
                    response.completionHandler.run();
                }
            }
        };
    }

    @FunctionalInterface
    private interface ExceptionHandler<T, E extends Exception>
    {
        T handleException(Request request, Exception exception)
                throws E;
    }

    @SuppressWarnings("unused")
    private sealed interface InternalResponse<T> {}

    private record InternalExceptionResponse<T>(T exceptionResponse)
            implements InternalResponse<T>
    {
        private InternalExceptionResponse
        {
            requireNonNull(exceptionResponse, "exceptionResponse is null");
        }
    }

    private record InternalStandardResponse<T>(JettyResponse jettyResponse, Runnable completionHandler)
            implements InternalResponse<T>
    {
        private InternalStandardResponse
        {
            requireNonNull(jettyResponse, "jettyResponse is null");
            requireNonNull(completionHandler, "completionHandler is null");
        }
    }

    private <T, E extends Exception> InternalResponse<T> internalExecute(Request request, ExceptionHandler<T, E> exceptionHandler, Span span)
            throws E
    {
        long requestStart = System.nanoTime();

        // create jetty request and response listener
        JettyRequestListener requestListener = new JettyRequestListener(request.getUri());
        HttpRequest jettyRequest = buildJettyRequest(request, requestListener);
        InputStreamResponseListener listener = new InputStreamResponseListener()
        {
            @Override
            public void onBegin(Response response)
            {
                callHttpStatusListeners(response);
            }

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

        RequestSizeListener requestSize = new RequestSizeListener();
        jettyRequest.onRequestContent(requestSize);

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
            return new InternalExceptionResponse<>(exceptionHandler.handleException(request, e));
        }
        catch (TimeoutException e) {
            stats.recordRequestFailed();
            requestLogger.log(requestInfo, ResponseInfo.failed(Optional.empty(), Optional.of(e)));
            jettyRequest.abort(e);
            return new InternalExceptionResponse<>(exceptionHandler.handleException(request, e));
        }
        catch (ExecutionException e) {
            stats.recordRequestFailed();
            requestLogger.log(requestInfo, ResponseInfo.failed(Optional.empty(), Optional.of(e)));
            Throwable cause = e.getCause();
            if (cause instanceof Exception) {
                return new InternalExceptionResponse<>(exceptionHandler.handleException(request, (Exception) cause));
            }
            return new InternalExceptionResponse<>(exceptionHandler.handleException(request, new RuntimeException(cause)));
        }

        // record attributes
        span.setAttribute(HttpAttributes.HTTP_RESPONSE_STATUS_CODE, response.getStatus());

        // negotiated http version
        span.setAttribute(NetworkAttributes.NETWORK_PROTOCOL_NAME, "HTTP"); // https://osi-model.com/application-layer/
        span.setAttribute(NetworkAttributes.NETWORK_PROTOCOL_VERSION, getHttpVersion(response.getVersion()));

        if (request.getBodyGenerator() != null) {
            span.setAttribute(HttpIncubatingAttributes.HTTP_REQUEST_BODY_SIZE, requestSize.getBytes());
        }

        // process response
        long responseStart = System.nanoTime();

        try {
            JettyResponse jettyResponse = new JettyResponse(response, listener.getInputStream());
            Runnable completionHandler = buildCompletionHandler(request, span, jettyResponse, requestSize, requestStart, responseStart);
            return new InternalStandardResponse<>(jettyResponse, completionHandler);
        }
        catch (Throwable e) {
            Runnable completionHandler = buildCompletionHandler(request, span, null, requestSize, requestStart, responseStart);
            try {
                throw propagate(request, e);
            }
            finally {
                completionHandler.run();
            }
        }
    }

    private Runnable buildCompletionHandler(Request request, Span span, JettyResponse jettyResponse, RequestSizeListener requestSize, long requestStart, long responseStart)
    {
        return () -> {
            if (jettyResponse != null) {
                try {
                    jettyResponse.getInputStream().close();
                }
                catch (IOException ignored) {
                    // ignore errors closing the stream
                }
                span.setAttribute(HttpIncubatingAttributes.HTTP_RESPONSE_BODY_SIZE, jettyResponse.getBytesRead());
            }
            if (recordRequestComplete) {
                recordRequestComplete(stats, request, requestSize.getBytes(), requestStart, jettyResponse, responseStart);
            }
        };
    }

    static String getHttpVersion(HttpVersion version)
    {
        // According to the RFCs:
        return switch (version) {
            case HTTP_0_9 -> "0.9"; // https://datatracker.ietf.org/doc/html/rfc1945
            case HTTP_1_0 -> "1.0"; // https://datatracker.ietf.org/doc/html/rfc1945
            case HTTP_1_1 -> "1.1"; // https://datatracker.ietf.org/doc/html/rfc2616
            case HTTP_2 -> "2"; // https://datatracker.ietf.org/doc/html/rfc9113
            case HTTP_3 -> "3"; // https://datatracker.ietf.org/doc/html/rfc9114
        };
    }

    @Override
    public <T, E extends Exception> HttpResponseFuture<T> executeAsync(Request request, ResponseHandler<T, E> responseHandler)
    {
        requireNonNull(request, "request is null");
        requireNonNull(responseHandler, "responseHandler is null");

        try {
            request = applyRequestFilters(request);
        }
        catch (RuntimeException e) {
            startSpan(request)
                    .setStatus(StatusCode.ERROR, e.getMessage())
                    .recordException(e, Attributes.of(ExceptionAttributes.EXCEPTION_ESCAPED, true))
                    .end();
            return new FailedHttpResponseFuture<>(e);
        }

        Span span = startSpan(request);
        request = injectTracing(request, span);

        HttpRequest jettyRequest = buildJettyRequest(request, new JettyRequestListener(request.getUri()));

        RequestSizeListener requestSize = new RequestSizeListener();
        jettyRequest.onRequestContent(requestSize);

        JettyResponseFuture<T, E> future = new JettyResponseFuture<>(request, jettyRequest, requestSize::getBytes, responseHandler, span, stats, recordRequestComplete);

        BufferingResponseListener listener = new BufferingResponseListener(future, Ints.saturatedCast(request.getMaxContentLength().orElse(maxContentLength).toBytes()))
        {
            @Override
            public void onBegin(Response response)
            {
                callHttpStatusListeners(response);
            }
        };

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

    private void callHttpStatusListeners(Response response)
    {
        httpStatusListeners.forEach(listener -> {
            try {
                listener.statusReceived(response.getStatus());
            }
            catch (Exception e) {
                response.abort(e);
            }
        });
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

    private Span startSpan(Request request)
    {
        String method = request.getMethod().toUpperCase(ENGLISH);
        int port = normalizePort(request.getUri().getScheme(), request.getUri().getPort());
        return request.getSpanBuilder()
                .orElseGet(() -> tracer.spanBuilder(name + " " + method))
                .setSpanKind(SpanKind.CLIENT)
                .setAttribute(CLIENT_NAME, name)
                .setAttribute(UrlAttributes.URL_FULL, request.getUri().toString())
                .setAttribute(HttpAttributes.HTTP_REQUEST_METHOD, method)
                .setAttribute(ServerAttributes.SERVER_ADDRESS, request.getUri().getHost())
                .setAttribute(ServerAttributes.SERVER_PORT, (long) port)
                .startSpan();
    }

    @SuppressWarnings("DataFlowIssue")
    private Request injectTracing(Request request, Span span)
    {
        Context context = Context.current().with(span);
        Request.Builder builder = Request.Builder.fromRequest(request);
        propagator.inject(context, builder, Request.Builder::addHeader);
        return builder.build();
    }

    private HttpRequest buildJettyRequest(Request finalRequest, JettyRequestListener listener)
    {
        HttpRequest jettyRequest = (HttpRequest) httpClient.newRequest(finalRequest.getUri());
        finalRequest.getHttpVersion().ifPresent(version -> {
            switch (version) {
                case HTTP_1 -> jettyRequest.version(HttpVersion.HTTP_1_1);
                case HTTP_2 -> jettyRequest.version(HttpVersion.HTTP_2);
                case HTTP_3 -> jettyRequest.version(HttpVersion.HTTP_3);
            }
        });
        jettyRequest.onRequestBegin(request -> listener.onRequestBegin());
        jettyRequest.onRequestSuccess(request -> listener.onRequestEnd());
        jettyRequest.onResponseBegin(response -> listener.onResponseBegin());
        jettyRequest.onComplete(result -> listener.onFinish());
        jettyRequest.onComplete(result -> {
            if (result.isFailed() && shouldBeDiagnosed(result)) {
                clientDiagnostics.logDiagnosticsInfo(httpClient);
            }
        });

        jettyRequest.attribute(STATS_KEY, listener);

        jettyRequest.method(finalRequest.getMethod());

        jettyRequest.headers(headers -> finalRequest.getHeaders().forEach(headers::add));
        BodyGenerator bodyGenerator = finalRequest.getBodyGenerator();
        if (bodyGenerator != null) {
            switch (bodyGenerator) {
                case StaticBodyGenerator generator -> jettyRequest.body(new BytesRequestContent(generator.getBody()));
                case ByteBufferBodyGenerator generator -> jettyRequest.body(new ByteBufferRequestContent(generator.getByteBuffers()));
                case FileBodyGenerator generator -> jettyRequest.body(fileContent(generator.getPath()));
                case StreamingBodyGenerator generator -> jettyRequest.body(new InputStreamRequestContent(generator.source()));
            }
        }

        jettyRequest.followRedirects(finalRequest.isFollowRedirects());

        setPreserveAuthorization(jettyRequest, finalRequest.isPreserveAuthorizationOnRedirect());

        // timeouts
        jettyRequest.timeout(finalRequest.getRequestTimeout().orElse(requestTimeout).toMillis(), MILLISECONDS);
        jettyRequest.idleTimeout(finalRequest.getIdleTimeout().orElse(idleTimeout).toMillis(), MILLISECONDS);

        return jettyRequest;
    }

    private boolean shouldBeDiagnosed(Result result)
    {
        return result.getFailure() instanceof TimeoutException || result.getFailure() instanceof RejectedExecutionException;
    }

    private static PathRequestContent fileContent(Path path)
    {
        try {
            return new PathRequestContent(path);
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public List<HttpRequestFilter> getRequestFilters()
    {
        return requestFilters;
    }

    public List<HttpStatusListener> getStatusListeners()
    {
        return httpStatusListeners;
    }

    public long getRequestTimeoutMillis()
    {
        return requestTimeout.toMillis();
    }

    @Override
    @Managed
    @Flatten
    public RequestStats getStats()
    {
        return stats;
    }

    @Managed
    @Nested
    public MonitoredQueuedThreadPoolMBean getThreadPool()
    {
        return monitoredQueuedThreadPoolMBean;
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
        return httpClient.getDestinations().stream()
                .filter(destination -> Objects.equals(destination.getOrigin().getScheme(), uri.getScheme()))
                .filter(destination -> Objects.equals(destination.getOrigin().getAddress().getHost(), uri.getHost()))
                .filter(destination -> destination.getOrigin().getAddress().getPort() == uri.getPort())
                .findFirst()
                .map(JettyHttpClient::dumpDestination)
                .orElse(null);
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

    private static List<org.eclipse.jetty.client.Request> getRequestForDestination(Destination destination)
    {
        HttpDestination httpDestination = (HttpDestination) destination;
        Queue<HttpExchange> httpExchanges = httpDestination.getHttpExchanges();

        List<org.eclipse.jetty.client.Request> requests = httpExchanges.stream()
                .map(HttpExchange::getRequest)
                .collect(Collectors.toList());

        getActiveConnections((AbstractConnectionPool) httpDestination.getConnectionPool()).stream()
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

    static void recordRequestComplete(RequestStats requestStats, Request request, long requestBytes, long requestStart, JettyResponse response, long responseStart)
    {
        if (response == null) {
            return;
        }

        Duration responseProcessingTime = Duration.nanosSince(responseStart);
        Duration requestProcessingTime = new Duration(responseStart - requestStart, NANOSECONDS);

        requestStats.recordResponseReceived(request.getMethod(),
                response.getStatusCode(),
                requestBytes,
                response.getBytesRead(),
                requestProcessingTime,
                responseProcessingTime);
    }

    private static class RequestSizeListener
            implements org.eclipse.jetty.client.Request.ContentListener
    {
        private long bytes;

        @Override
        public void onContent(org.eclipse.jetty.client.Request request, ByteBuffer content)
        {
            bytes += content.remaining();
        }

        public long getBytes()
        {
            return bytes;
        }
    }
}
