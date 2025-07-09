/*
 * Copyright 2010 Proofpoint, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.airlift.http.server;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import io.airlift.http.server.HttpServerBinder.HttpResourceBinding;
import io.airlift.http.server.jetty.MonitoredQueuedThreadPoolMBean;
import io.airlift.log.Logger;
import io.airlift.node.NodeInfo;
import io.airlift.units.DataSize;
import io.airlift.units.Duration;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.servlet.Filter;
import jakarta.servlet.Servlet;
import org.eclipse.jetty.alpn.server.ALPNServerConnectionFactory;
import org.eclipse.jetty.compression.Compression;
import org.eclipse.jetty.compression.server.CompressionConfig;
import org.eclipse.jetty.compression.server.CompressionHandler;
import org.eclipse.jetty.ee11.servlet.FilterHolder;
import org.eclipse.jetty.ee11.servlet.ServletContextHandler;
import org.eclipse.jetty.ee11.servlet.ServletHolder;
import org.eclipse.jetty.http.UriCompliance;
import org.eclipse.jetty.http2.RateControl;
import org.eclipse.jetty.http2.server.AuthorityCustomizer;
import org.eclipse.jetty.http2.server.HTTP2CServerConnectionFactory;
import org.eclipse.jetty.http2.server.HTTP2ServerConnectionFactory;
import org.eclipse.jetty.io.ArrayByteBufferPool;
import org.eclipse.jetty.io.ByteBufferPool;
import org.eclipse.jetty.io.ConnectionStatistics;
import org.eclipse.jetty.jmx.MBeanContainer;
import org.eclipse.jetty.server.ConnectionFactory;
import org.eclipse.jetty.server.ForwardedRequestCustomizer;
import org.eclipse.jetty.server.HostHeaderCustomizer;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.SecureRequestCustomizer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.SslConnectionFactory;
import org.eclipse.jetty.server.handler.ErrorHandler;
import org.eclipse.jetty.server.handler.GracefulHandler;
import org.eclipse.jetty.server.handler.StatisticsHandler;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.util.thread.MonitoredQueuedThreadPool;
import org.eclipse.jetty.util.thread.ScheduledExecutorScheduler;
import org.eclipse.jetty.util.thread.Scheduler;
import org.eclipse.jetty.util.thread.VirtualThreadPool;
import org.weakref.jmx.Managed;
import org.weakref.jmx.Nested;

import javax.management.MBeanServer;

import java.io.IOException;
import java.nio.channels.ServerSocketChannel;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;

import static com.google.common.base.MoreObjects.firstNonNull;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Throwables.throwIfUnchecked;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static io.airlift.concurrent.Threads.daemonThreadsNamed;
import static io.airlift.http.server.ServerFeature.CASE_SENSITIVE_HEADER_CACHE;
import static io.airlift.http.server.ServerFeature.LEGACY_URI_COMPLIANCE;
import static io.airlift.http.server.ServerFeature.VIRTUAL_THREADS;
import static java.lang.Math.max;
import static java.lang.Math.toIntExact;
import static java.time.temporal.ChronoUnit.DAYS;
import static java.util.Collections.list;
import static java.util.Comparator.naturalOrder;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.Executors.newSingleThreadScheduledExecutor;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.eclipse.jetty.http.MimeTypes.Type.TEXT_PLAIN;
import static org.eclipse.jetty.http.UriCompliance.Violation.AMBIGUOUS_PATH_ENCODING;
import static org.eclipse.jetty.http.UriCompliance.Violation.AMBIGUOUS_PATH_SEPARATOR;
import static org.eclipse.jetty.http.UriCompliance.Violation.SUSPICIOUS_PATH_CHARACTERS;

public class HttpServer
{
    public enum ClientCertificate
    {
        NONE, REQUESTED, REQUIRED
    }

    private static final Logger log = Logger.get(HttpServer.class);

    private final Server server;
    private final MonitoredQueuedThreadPoolMBean monitoredQueuedThreadPoolMBean;
    private ConnectionStats httpConnectionStats;
    private ConnectionStats httpsConnectionStats;
    private ScheduledExecutorService scheduledExecutorService;
    private Optional<SslContextFactory.Server> sslContextFactory;

    public HttpServer(
            String name,
            HttpServerInfo httpServerInfo,
            NodeInfo nodeInfo,
            HttpServerConfig config,
            Optional<HttpsConfig> maybeHttpsConfig,
            Servlet servlet,
            Set<Filter> filters,
            Set<HttpResourceBinding> resources,
            Set<ServerFeature> serverFeatures,
            ClientCertificate clientCertificate,
            Optional<MBeanServer> mbeanServer,
            Optional<SslContextFactory.Server> maybeSslContextFactory)
            throws IOException
    {
        requireNonNull(name, "name is null");
        requireNonNull(httpServerInfo, "httpServerInfo is null");
        requireNonNull(nodeInfo, "nodeInfo is null");
        requireNonNull(config, "config is null");
        requireNonNull(maybeHttpsConfig, "httpsConfig is null");
        requireNonNull(servlet, "servlet is null");
        requireNonNull(maybeSslContextFactory, "maybeSslContextFactory is null");
        requireNonNull(clientCertificate, "clientCertificate is null");
        requireNonNull(mbeanServer, "mbeanServer is null");

        checkArgument(!config.isHttpsEnabled() || maybeHttpsConfig.isPresent(), "httpsConfig must be present when HTTPS is enabled");
        MonitoredQueuedThreadPool threadPool = new MonitoredQueuedThreadPool(config.getMaxThreads());
        threadPool.setMinThreads(config.getMinThreads());
        threadPool.setIdleTimeout(toIntExact(config.getThreadMaxIdleTime().toMillis()));
        threadPool.setName(name + "-worker");
        threadPool.setDetailedDump(true);
        if (serverFeatures.contains(VIRTUAL_THREADS)) {
            VirtualThreadPool virtualExecutor = new VirtualThreadPool();
            virtualExecutor.setMaxThreads(config.getMaxThreads());
            virtualExecutor.setName(name + "-worker#v");
            virtualExecutor.setDetailedDump(true);
            log.info("Virtual threads support is enabled");
            threadPool.setVirtualThreadsExecutor(virtualExecutor);
        }

        int maxBufferSize = toIntExact(max(max(
                toSafeBytes(config.getMaxRequestHeaderSize()).orElse(8192),
                toSafeBytes(config.getMaxResponseHeaderSize()).orElse(8192)),
                toSafeBytes(config.getOutputBufferSize()).orElse(32768)));

        server = new Server(threadPool, createScheduler(name + "-scheduler"), createByteBufferPool(maxBufferSize, config));
        server.setName(name);
        server.setStopAtShutdown(true);
        server.setStopTimeout(config.getStopTimeout().toMillis());

        this.monitoredQueuedThreadPoolMBean = new MonitoredQueuedThreadPoolMBean(threadPool);

        boolean showStackTrace = config.isShowStackTrace();
        boolean enableCompression = config.isCompressionEnabled();

        this.sslContextFactory = maybeSslContextFactory;

        if (mbeanServer.isPresent()) {
            // export jmx mbeans if a server was provided
            server.addBean(new MBeanContainer(mbeanServer.orElseThrow()));
        }

        HttpConfiguration baseHttpConfiguration = new HttpConfiguration();
        baseHttpConfiguration.setSendServerVersion(false);
        baseHttpConfiguration.setSendXPoweredBy(false);
        baseHttpConfiguration.setNotifyRemoteAsyncErrors(config.isNotifyRemoteAsyncErrors());

        baseHttpConfiguration.addCustomizer(switch (config.getProcessForwarded()) {
            case REJECT -> new RejectForwardedRequestCustomizer();
            case ACCEPT -> new ForwardedRequestCustomizer();
            case IGNORE -> new IgnoreForwardedRequestCustomizer();
        });

        // Adds :authority pseudoheader on HTTP/2
        baseHttpConfiguration.addCustomizer(new AuthorityCustomizer());

        // Adds :host header on HTTP/1.0 and HTTP/2
        baseHttpConfiguration.addCustomizer(new HostHeaderCustomizer());

        if (config.getMaxRequestHeaderSize() != null) {
            baseHttpConfiguration.setRequestHeaderSize(toIntExact(config.getMaxRequestHeaderSize().toBytes()));
        }
        if (config.getMaxResponseHeaderSize() != null) {
            baseHttpConfiguration.setResponseHeaderSize(toIntExact(config.getMaxResponseHeaderSize().toBytes()));
        }
        if (config.getOutputBufferSize() != null) {
            baseHttpConfiguration.setOutputBufferSize(toIntExact(config.getOutputBufferSize().toBytes()));
        }

        // see https://bugs.eclipse.org/bugs/show_bug.cgi?id=414449#c4
        baseHttpConfiguration.setHeaderCacheCaseSensitive(serverFeatures.contains(CASE_SENSITIVE_HEADER_CACHE));

        if (serverFeatures.contains(LEGACY_URI_COMPLIANCE)) {
            // allow encoded slashes to occur in URI paths
            UriCompliance uriCompliance = UriCompliance.from(EnumSet.of(AMBIGUOUS_PATH_SEPARATOR, AMBIGUOUS_PATH_ENCODING, SUSPICIOUS_PATH_CHARACTERS));
            baseHttpConfiguration.setUriCompliance(uriCompliance);
        }

        // set up HTTP connector
        ServerConnector httpConnector;
        if (config.isHttpEnabled()) {
            HttpConfiguration httpConfiguration = new HttpConfiguration(baseHttpConfiguration);
            // if https is enabled, set the CONFIDENTIAL and INTEGRAL redirection information
            if (config.isHttpsEnabled()) {
                httpConfiguration.setSecureScheme("https");
                httpConfiguration.setSecurePort(httpServerInfo.getHttpsUri().getPort());
            }

            Integer acceptors = config.getHttpAcceptorThreads();
            Integer selectors = config.getHttpSelectorThreads();
            httpConnector = createServerConnector(
                    httpServerInfo.getHttpChannel(),
                    server,
                    null,
                    firstNonNull(acceptors, -1),
                    firstNonNull(selectors, -1),
                    insecureFactories(config, httpConfiguration));
            httpConnector.setName("http");
            httpConnector.setPort(httpServerInfo.getHttpUri().getPort());
            httpConnector.setIdleTimeout(config.getNetworkMaxIdleTime().toMillis());
            httpConnector.setHost(nodeInfo.getBindIp().getHostAddress());
            httpConnector.setAcceptQueueSize(config.getHttpAcceptQueueSize());

            // track connection statistics
            ConnectionStatistics connectionStats = new ConnectionStatistics();
            httpConnector.addBean(connectionStats);
            this.httpConnectionStats = new ConnectionStats(connectionStats);
            server.addConnector(httpConnector);
        }

        // set up NIO-based HTTPS connector
        ServerConnector httpsConnector;
        if (config.isHttpsEnabled()) {
            HttpConfiguration httpsConfiguration = new HttpConfiguration(baseHttpConfiguration);
            setSecureRequestCustomizer(httpsConfiguration);

            HttpsConfig httpsConfig = maybeHttpsConfig.orElseThrow();
            this.sslContextFactory = Optional.of(this.sslContextFactory.orElseGet(() -> createReloadingSslContextFactory(name, httpsConfig, clientCertificate, nodeInfo.getEnvironment())));
            Integer acceptors = config.getHttpsAcceptorThreads();
            Integer selectors = config.getHttpsSelectorThreads();
            httpsConnector = createServerConnector(
                    httpServerInfo.getHttpsChannel(),
                    server,
                    null,
                    firstNonNull(acceptors, -1),
                    firstNonNull(selectors, -1),
                    secureFactories(config, httpsConfiguration, sslContextFactory.get()));
            httpsConnector.setName("https");
            httpsConnector.setPort(httpServerInfo.getHttpsUri().getPort());
            httpsConnector.setIdleTimeout(config.getNetworkMaxIdleTime().toMillis());
            httpsConnector.setHost(nodeInfo.getBindIp().getHostAddress());
            httpsConnector.setAcceptQueueSize(config.getHttpAcceptQueueSize());

            // track connection statistics
            ConnectionStatistics connectionStats = new ConnectionStatistics();
            httpsConnector.addBean(connectionStats);
            this.httpsConnectionStats = new ConnectionStats(connectionStats);
            server.addConnector(httpsConnector);
        }

        /*
         * Jetty's handlers chain is:
         *    channel listener (protocol)
         *    |--- graceful handler (tracks active requests)
         *         |--- statistics handler
         *              |--- compression handler (if enabled)
         *                   |--- servlet context handler
         *                        |--- error handler
         *                        |--- servlet filters (i.e. tracing)
         *                        |--- the servlet (i.e. Jersey's ServletContainer)
         *                        |--- static resources
         *    |--- error handler
         */
        StatisticsHandler statsHandler = new StatisticsHandler();

        ServletContextHandler servletContext = createServletContext(servlet, resources, filters, Set.of("http", "https"), showStackTrace, serverFeatures.contains(LEGACY_URI_COMPLIANCE));

        if (enableCompression) {
            CompressionHandler compressionHandler = new CompressionHandler();

            Iterator<Compression> loader = ServiceLoader.load(Compression.class, HttpServer.class.getClassLoader())
                    .iterator();

            while (loader.hasNext()) {
                try {
                    compressionHandler.putCompression(loader.next());
                }
                catch (Throwable t) {
                    log.error(t, "Error loading http server compression");
                }
            }

            CompressionConfig compressionConfig = CompressionConfig.builder()
                    .defaults()
                    .build();

            compressionHandler.putConfiguration("/*", compressionConfig);
            compressionHandler.setHandler(servletContext);

            statsHandler.setHandler(compressionHandler);
        }
        else {
            statsHandler.setHandler(servletContext);
        }

        if (config.isLogEnabled()) {
            server.setRequestLog(new JettyRequestLog(
                    config.getLogPath(),
                    config.getLogHistory(),
                    config.getLogQueueSize(),
                    config.getLogMaxFileSize().toBytes(),
                    config.isCompressionEnabled(),
                    config.isLogImmediateFlush()));
        }
        server.setHandler(new GracefulHandler(statsHandler));
        ErrorHandler errorHandler = new ErrorHandler();
        errorHandler.setShowMessageInTitle(showStackTrace);
        errorHandler.setShowStacks(showStackTrace);
        errorHandler.setDefaultResponseMimeType(TEXT_PLAIN.asString());
        server.setErrorHandler(errorHandler);
    }

    private ByteBufferPool createByteBufferPool(int maxBufferSize, HttpServerConfig config)
    {
        long maxHeapMemory = config.getMaxHeapMemory().map(DataSize::toBytes).orElse(0L); // Use default heuristics for max heap memory
        long maxOffHeapMemory = config.getMaxDirectMemory().map(DataSize::toBytes).orElse(0L); // Use default heuristics for max off heap memory

        ArrayByteBufferPool pool = config.isTrackMemoryAllocations() ?
                new ArrayByteBufferPool.Tracking(
                    0,
                    maxBufferSize,
                    Integer.MAX_VALUE,
                    maxHeapMemory,
                    maxOffHeapMemory) :
                new ArrayByteBufferPool.Quadratic(
                    0,
                    maxBufferSize,
                    Integer.MAX_VALUE,
                    maxHeapMemory,
                    maxOffHeapMemory);

        pool.setStatisticsEnabled(true);
        return pool;
    }

    private ConnectionFactory[] insecureFactories(HttpServerConfig config, HttpConfiguration httpConfiguration)
    {
        HttpConnectionFactory http1 = new HttpConnectionFactory(httpConfiguration);
        HTTP2CServerConnectionFactory http2c = new HTTP2CServerConnectionFactory(httpConfiguration);
        http2c.setInitialSessionRecvWindow(toIntExact(config.getHttp2InitialSessionReceiveWindowSize().toBytes()));
        http2c.setInitialStreamRecvWindow(toIntExact(config.getHttp2InitialStreamReceiveWindowSize().toBytes()));
        http2c.setMaxConcurrentStreams(config.getHttp2MaxConcurrentStreams());
        http2c.setInputBufferSize(toIntExact(config.getHttp2InputBufferSize().toBytes()));
        http2c.setStreamIdleTimeout(config.getHttp2StreamIdleTimeout().toMillis());
        http2c.setRateControlFactory(new RateControl.Factory() {}); // disable rate control

        return new ConnectionFactory[] {http1, http2c};
    }

    private ConnectionFactory[] secureFactories(HttpServerConfig config, HttpConfiguration httpsConfiguration, SslContextFactory.Server server)
    {
        ConnectionFactory http1 = new HttpConnectionFactory(httpsConfiguration);
        ALPNServerConnectionFactory alpn = new ALPNServerConnectionFactory();
        alpn.setDefaultProtocol(http1.getProtocol());

        SslConnectionFactory tls = new SslConnectionFactory(server, alpn.getProtocol());

        HTTP2ServerConnectionFactory http2 = new HTTP2ServerConnectionFactory(httpsConfiguration);
        http2.setInitialSessionRecvWindow(toIntExact(config.getHttp2InitialSessionReceiveWindowSize().toBytes()));
        http2.setInitialStreamRecvWindow(toIntExact(config.getHttp2InitialStreamReceiveWindowSize().toBytes()));
        http2.setMaxConcurrentStreams(config.getHttp2MaxConcurrentStreams());
        http2.setInputBufferSize(toIntExact(config.getHttp2InputBufferSize().toBytes()));
        http2.setStreamIdleTimeout(config.getHttp2StreamIdleTimeout().toMillis());
        http2.setRateControlFactory(new RateControl.Factory() {}); // disable rate control

        return new ConnectionFactory[] {tls, alpn, http2, http1};
    }

    private static void setSecureRequestCustomizer(HttpConfiguration configuration)
    {
        configuration.setCustomizers(ImmutableList.<HttpConfiguration.Customizer>builder()
                .add(new SecureRequestCustomizer(false))
                .addAll(configuration.getCustomizers())
                .build());
    }

    private static ServletContextHandler createServletContext(Servlet servlet,
            Set<HttpResourceBinding> resources,
            Set<Filter> filters,
            Set<String> connectorNames,
            boolean showStackTrace,
            boolean enableLegacyUriCompliance)
    {
        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.NO_SESSIONS);
        ErrorHandler handler = new ErrorHandler();
        handler.setShowStacks(showStackTrace);
        handler.setShowMessageInTitle(showStackTrace);
        context.setErrorHandler(handler);

        if (enableLegacyUriCompliance) {
            // allow encoded slashes to occur in URI paths
            context.getServletHandler().setDecodeAmbiguousURIs(true);
        }
        // -- user provided filters
        for (Filter filter : filters) {
            context.addFilter(new FilterHolder(filter), "/*", null);
        }
        // -- static resources
        for (HttpResourceBinding resource : resources) {
            ClassPathResourceFilter filter = new ClassPathResourceFilter(
                    resource.getBaseUri(),
                    resource.getClassPathResourceBase(),
                    resource.getWelcomeFiles());
            context.addFilter(new FilterHolder(filter), filter.getBaseUri() + "/*", null);
        }
        // -- the servlet
        ServletHolder servletHolder = new ServletHolder(servlet);
        context.addServlet(servletHolder, "/*");

        // Starting with Jetty 9 there is no way to specify connectors directly, but
        // there is this wonky @ConnectorName virtual hosts automatically added
        List<String> virtualHosts = connectorNames.stream()
                .map(connectorName -> "@" + connectorName)
                .collect(toImmutableList());

        context.setVirtualHosts(virtualHosts);
        return context;
    }

    @VisibleForTesting
    Set<X509Certificate> getCertificates()
    {
        ImmutableSet.Builder<X509Certificate> certificates = ImmutableSet.builder();
        this.sslContextFactory.ifPresent(factory -> {
            try {
                KeyStore keystore = factory.getKeyStore();
                for (String alias : list(keystore.aliases())) {
                    Certificate certificate = keystore.getCertificate(alias);
                    if (certificate instanceof X509Certificate) {
                        certificates.add((X509Certificate) certificate);
                    }
                }
            }
            catch (Exception e) {
                log.error(e, "Error reading certificates");
            }
        });

        return certificates.build();
    }

    @Managed
    public Long getDaysUntilCertificateExpiration()
    {
        return getCertificates().stream()
                .map(X509Certificate::getNotAfter)
                .min(naturalOrder())
                .map(date -> ZonedDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault()))
                .map(date -> ZonedDateTime.now().until(date, DAYS))
                .orElse(null);
    }

    @Managed
    @Nested
    public ConnectionStats getHttpConnectionStats()
    {
        return httpConnectionStats;
    }

    @Managed
    @Nested
    public ConnectionStats getHttpsConnectionStats()
    {
        return httpsConnectionStats;
    }

    @Managed
    @Nested
    public MonitoredQueuedThreadPoolMBean getServerThreadPool()
    {
        return monitoredQueuedThreadPoolMBean;
    }

    @PostConstruct
    public void start()
            throws Exception
    {
        server.start();
        checkState(server.isStarted(), "server is not started");
    }

    @PreDestroy
    public void stop()
            throws Exception
    {
        long activeRequests = server.getHandlers().stream()
                .filter(GracefulHandler.class::isInstance)
                .map(GracefulHandler.class::cast)
                .findFirst()
                .map(GracefulHandler::getCurrentRequestCount)
                .orElse(0L);

        log.debug("Server %s stopping in %s, %d active requests to complete", Duration.succinctDuration(server.getStopTimeout(), MILLISECONDS), activeRequests);
        server.stop();

        if (scheduledExecutorService != null) {
            scheduledExecutorService.shutdown();
        }

        log.info("Server %s shutdown complete", server.getName());
    }

    @VisibleForTesting
    void join()
            throws InterruptedException
    {
        server.join();
    }

    private SslContextFactory.Server createReloadingSslContextFactory(String name, HttpsConfig config, ClientCertificate clientCertificate, String environment)
    {
        if (scheduledExecutorService == null) {
            scheduledExecutorService = newSingleThreadScheduledExecutor(daemonThreadsNamed(name + "-ssl-reloader"));
        }

        return new ReloadableSslContextFactoryProvider(config, scheduledExecutorService, clientCertificate, environment).getSslContextFactory();
    }

    private static ServerConnector createServerConnector(
            ServerSocketChannel channel,
            Server server,
            Executor executor,
            int acceptors,
            int selectors,
            ConnectionFactory... factories)
            throws IOException
    {
        ServerConnector connector = new ServerConnector(server, executor, null, null, acceptors, selectors, factories);
        connector.open(channel);
        return connector;
    }

    private static OptionalLong toSafeBytes(DataSize dataSize)
    {
        if (dataSize == null) {
            return OptionalLong.empty();
        }

        return OptionalLong.of(dataSize.toBytes());
    }

    private static Scheduler createScheduler(String name)
    {
        Scheduler scheduler = new ScheduledExecutorScheduler(name, true);
        try {
            scheduler.start();
        }
        catch (Exception e) {
            throwIfUnchecked(e);
            throw new RuntimeException(e);
        }

        return scheduler;
    }
}
