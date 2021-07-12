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
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import io.airlift.event.client.EventClient;
import io.airlift.http.server.HttpServerBinder.HttpResourceBinding;
import io.airlift.log.Logger;
import io.airlift.node.NodeInfo;
import io.airlift.tracetoken.TraceTokenManager;
import org.eclipse.jetty.http2.server.HTTP2CServerConnectionFactory;
import org.eclipse.jetty.io.ConnectionStatistics;
import org.eclipse.jetty.jmx.MBeanContainer;
import org.eclipse.jetty.security.ConstraintMapping;
import org.eclipse.jetty.security.ConstraintSecurityHandler;
import org.eclipse.jetty.security.LoginService;
import org.eclipse.jetty.security.SecurityHandler;
import org.eclipse.jetty.security.authentication.BasicAuthenticator;
import org.eclipse.jetty.server.ConnectionFactory;
import org.eclipse.jetty.server.ForwardedRequestCustomizer;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.SecureRequestCustomizer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.SslConnectionFactory;
import org.eclipse.jetty.server.handler.HandlerCollection;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.server.handler.RequestLogHandler;
import org.eclipse.jetty.server.handler.StatisticsHandler;
import org.eclipse.jetty.server.handler.gzip.GzipHandler;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.security.Constraint;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.weakref.jmx.Managed;
import org.weakref.jmx.Nested;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.management.MBeanServer;
import javax.servlet.Filter;
import javax.servlet.Servlet;

import java.io.File;
import java.io.IOException;
import java.nio.channels.ServerSocketChannel;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;

import static com.google.common.base.MoreObjects.firstNonNull;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static io.airlift.concurrent.Threads.daemonThreadsNamed;
import static java.lang.Math.toIntExact;
import static java.lang.String.format;
import static java.time.temporal.ChronoUnit.DAYS;
import static java.util.Collections.list;
import static java.util.Comparator.naturalOrder;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.Executors.newSingleThreadScheduledExecutor;

public class HttpServer
{
    public enum ClientCertificate
    {
        NONE, REQUESTED, REQUIRED
    }

    private static final Logger log = Logger.get(HttpServer.class);

    private final Server server;
    private final boolean registerErrorHandler;
    private final DelimitedRequestLog requestLog;
    private ConnectionStats httpConnectionStats;
    private ConnectionStats httpsConnectionStats;
    private ScheduledExecutorService scheduledExecutorService;
    private Optional<SslContextFactory.Server> sslContextFactory;

    public HttpServer(
            HttpServerInfo httpServerInfo,
            NodeInfo nodeInfo,
            HttpServerConfig config,
            Optional<HttpsConfig> maybeHttpsConfig,
            Servlet theServlet,
            Map<String, String> parameters,
            Set<Filter> filters,
            Set<HttpResourceBinding> resources,
            Servlet theAdminServlet,
            Map<String, String> adminParameters,
            Set<Filter> adminFilters,
            ClientCertificate clientCertificate,
            MBeanServer mbeanServer,
            LoginService loginService,
            TraceTokenManager tokenManager,
            RequestStats stats,
            EventClient eventClient,
            Optional<SslContextFactory.Server> maybeSslContextFactory)
            throws IOException
    {
        requireNonNull(httpServerInfo, "httpServerInfo is null");
        requireNonNull(nodeInfo, "nodeInfo is null");
        requireNonNull(config, "config is null");
        requireNonNull(maybeHttpsConfig, "httpsConfig is null");
        requireNonNull(theServlet, "theServlet is null");
        requireNonNull(maybeSslContextFactory, "maybeSslContextFactory is null");
        requireNonNull(clientCertificate, "clientCertificate is null");

        checkArgument(!config.isHttpsEnabled() || maybeHttpsConfig.isPresent(), "httpsConfig must be present when HTTPS is enabled");

        QueuedThreadPool threadPool = new QueuedThreadPool(config.getMaxThreads());
        threadPool.setMinThreads(config.getMinThreads());
        threadPool.setIdleTimeout(toIntExact(config.getThreadMaxIdleTime().toMillis()));
        threadPool.setName("http-worker");
        threadPool.setDetailedDump(true);
        server = new Server(threadPool);
        registerErrorHandler = config.isShowStackTrace();

        this.sslContextFactory = maybeSslContextFactory;

        if (mbeanServer != null) {
            // export jmx mbeans if a server was provided
            MBeanContainer mbeanContainer = new MBeanContainer(mbeanServer);
            server.addBean(mbeanContainer);
        }

        HttpConfiguration baseHttpConfiguration = new HttpConfiguration();
        baseHttpConfiguration.setSendServerVersion(false);
        baseHttpConfiguration.setSendXPoweredBy(false);
        if (config.isProcessForwarded()) {
            baseHttpConfiguration.addCustomizer(new ForwardedRequestCustomizer());
        }
        if (config.getMaxRequestHeaderSize() != null) {
            baseHttpConfiguration.setRequestHeaderSize(toIntExact(config.getMaxRequestHeaderSize().toBytes()));
        }
        if (config.getMaxResponseHeaderSize() != null) {
            baseHttpConfiguration.setResponseHeaderSize(toIntExact(config.getMaxResponseHeaderSize().toBytes()));
        }

        // disable async error notifications to work around https://github.com/jersey/jersey/issues/3691
        baseHttpConfiguration.setNotifyRemoteAsyncErrors(false);

        // register a channel listener if logging is enabled
        HttpServerChannelListener channelListener = null;
        if (config.isLogEnabled()) {
            this.requestLog = createDelimitedRequestLog(config, tokenManager, eventClient);
            channelListener = new HttpServerChannelListener(this.requestLog);
        }
        else {
            this.requestLog = null;
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
            HttpConnectionFactory http1 = new HttpConnectionFactory(httpConfiguration);
            HTTP2CServerConnectionFactory http2c = new HTTP2CServerConnectionFactory(httpConfiguration);
            http2c.setInitialSessionRecvWindow(toIntExact(config.getHttp2InitialSessionReceiveWindowSize().toBytes()));
            http2c.setInitialStreamRecvWindow(toIntExact(config.getHttp2InitialStreamReceiveWindowSize().toBytes()));
            http2c.setMaxConcurrentStreams(config.getHttp2MaxConcurrentStreams());
            http2c.setInputBufferSize(toIntExact(config.getHttp2InputBufferSize().toBytes()));
            http2c.setStreamIdleTimeout(config.getHttp2StreamIdleTimeout().toMillis());
            httpConnector = createServerConnector(
                    httpServerInfo.getHttpChannel(),
                    server,
                    null,
                    firstNonNull(acceptors, -1),
                    firstNonNull(selectors, -1),
                    http1,
                    http2c);
            httpConnector.setName("http");
            httpConnector.setPort(httpServerInfo.getHttpUri().getPort());
            httpConnector.setIdleTimeout(config.getNetworkMaxIdleTime().toMillis());
            httpConnector.setHost(nodeInfo.getBindIp().getHostAddress());
            httpConnector.setAcceptQueueSize(config.getHttpAcceptQueueSize());

            // track connection statistics
            ConnectionStatistics connectionStats = new ConnectionStatistics();
            httpConnector.addBean(connectionStats);
            this.httpConnectionStats = new ConnectionStats(connectionStats);

            if (channelListener != null) {
                httpConnector.addBean(channelListener);
            }

            server.addConnector(httpConnector);
        }

        // set up NIO-based HTTPS connector
        ServerConnector httpsConnector;
        if (config.isHttpsEnabled()) {
            HttpConfiguration httpsConfiguration = new HttpConfiguration(baseHttpConfiguration);
            httpsConfiguration.addCustomizer(new SecureRequestCustomizer());

            HttpsConfig httpsConfig = maybeHttpsConfig.orElseThrow();
            this.sslContextFactory = Optional.of(this.sslContextFactory.orElseGet(() -> createReloadingSslContextFactory(httpsConfig, clientCertificate, nodeInfo.getEnvironment())));
            SslConnectionFactory sslConnectionFactory = new SslConnectionFactory(sslContextFactory.get(), "http/1.1");

            Integer acceptors = config.getHttpsAcceptorThreads();
            Integer selectors = config.getHttpsSelectorThreads();
            httpsConnector = createServerConnector(
                    httpServerInfo.getHttpsChannel(),
                    server,
                    null,
                    firstNonNull(acceptors, -1),
                    firstNonNull(selectors, -1),
                    sslConnectionFactory,
                    new HttpConnectionFactory(httpsConfiguration));
            httpsConnector.setName("https");
            httpsConnector.setPort(httpServerInfo.getHttpsUri().getPort());
            httpsConnector.setIdleTimeout(config.getNetworkMaxIdleTime().toMillis());
            httpsConnector.setHost(nodeInfo.getBindIp().getHostAddress());
            httpsConnector.setAcceptQueueSize(config.getHttpAcceptQueueSize());

            // track connection statistics
            ConnectionStatistics connectionStats = new ConnectionStatistics();
            httpsConnector.addBean(connectionStats);
            this.httpsConnectionStats = new ConnectionStats(connectionStats);

            if (channelListener != null) {
                httpsConnector.addBean(channelListener);
            }

            server.addConnector(httpsConnector);
        }

        // set up NIO-based Admin connector
        ServerConnector adminConnector;
        if (theAdminServlet != null && config.isAdminEnabled()) {
            HttpConfiguration adminConfiguration = new HttpConfiguration(baseHttpConfiguration);

            QueuedThreadPool adminThreadPool = new QueuedThreadPool(config.getAdminMaxThreads());
            adminThreadPool.setName("http-admin-worker");
            adminThreadPool.setMinThreads(config.getAdminMinThreads());
            adminThreadPool.setIdleTimeout(toIntExact(config.getThreadMaxIdleTime().toMillis()));

            if (config.isHttpsEnabled()) {
                adminConfiguration.addCustomizer(new SecureRequestCustomizer());

                HttpsConfig httpsConfig = maybeHttpsConfig.orElseThrow();
                this.sslContextFactory = Optional.of(this.sslContextFactory.orElseGet(() -> createReloadingSslContextFactory(httpsConfig, clientCertificate, nodeInfo.getEnvironment())));
                SslConnectionFactory sslConnectionFactory = new SslConnectionFactory(sslContextFactory.get(), "http/1.1");
                adminConnector = createServerConnector(
                        httpServerInfo.getAdminChannel(),
                        server,
                        adminThreadPool,
                        0,
                        -1,
                        sslConnectionFactory,
                        new HttpConnectionFactory(adminConfiguration));
            }
            else {
                HttpConnectionFactory http1 = new HttpConnectionFactory(adminConfiguration);
                HTTP2CServerConnectionFactory http2c = new HTTP2CServerConnectionFactory(adminConfiguration);
                http2c.setMaxConcurrentStreams(config.getHttp2MaxConcurrentStreams());
                adminConnector = createServerConnector(
                        httpServerInfo.getAdminChannel(),
                        server,
                        adminThreadPool,
                        -1,
                        -1,
                        http1,
                        http2c);
            }

            adminConnector.setName("admin");
            adminConnector.setPort(httpServerInfo.getAdminUri().getPort());
            adminConnector.setIdleTimeout(config.getNetworkMaxIdleTime().toMillis());
            adminConnector.setHost(nodeInfo.getBindIp().getHostAddress());
            adminConnector.setAcceptQueueSize(config.getHttpAcceptQueueSize());

            server.addConnector(adminConnector);
        }

        /*
         * structure is:
         *
         * server
         *    |--- statistics handler
         *           |--- context handler
         *           |       |--- trace token filter
         *           |       |--- gzip response filter
         *           |       |--- gzip request filter
         *           |       |--- security handler
         *           |       |--- user provided filters
         *           |       |--- the servlet (normally GuiceContainer)
         *           |       |--- resource handlers
         *           |--- log handler
         *    |-- admin context handler
         *           \ --- the admin servlet
         */
        HandlerCollection handlers = new HandlerCollection();

        handlers.addHandler(createServletContext(theServlet, resources, parameters, filters, tokenManager, loginService, "http", "https"));

        RequestLogHandler statsRecorder = new RequestLogHandler();
        statsRecorder.setRequestLog(new StatsRecordingHandler(stats));
        handlers.addHandler(statsRecorder);

        // add handlers to Jetty
        StatisticsHandler statsHandler = new StatisticsHandler();
        statsHandler.setHandler(handlers);

        HandlerList rootHandlers = new HandlerList();
        if (theAdminServlet != null && config.isAdminEnabled()) {
            rootHandlers.addHandler(createServletContext(theAdminServlet, resources, adminParameters, adminFilters, tokenManager, loginService, "admin"));
        }
        rootHandlers.addHandler(statsHandler);
        server.setHandler(rootHandlers);
    }

    private static ServletContextHandler createServletContext(Servlet theServlet,
            Set<HttpResourceBinding> resources,
            Map<String, String> parameters,
            Set<Filter> filters,
            TraceTokenManager tokenManager,
            LoginService loginService,
            String... connectorNames)
    {
        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.NO_SESSIONS);

        context.addFilter(new FilterHolder(new TimingFilter()), "/*", null);
        if (tokenManager != null) {
            context.addFilter(new FilterHolder(new TraceTokenFilter(tokenManager)), "/*", null);
        }

        // -- security handler
        if (loginService != null) {
            SecurityHandler securityHandler = createSecurityHandler(loginService);
            context.setSecurityHandler(securityHandler);
        }
        // -- user provided filters
        for (Filter filter : filters) {
            context.addFilter(new FilterHolder(filter), "/*", null);
        }
        // -- static resources
        for (HttpResourceBinding resource : resources) {
            ClassPathResourceFilter servlet = new ClassPathResourceFilter(
                    resource.getBaseUri(),
                    resource.getClassPathResourceBase(),
                    resource.getWelcomeFiles());
            context.addFilter(new FilterHolder(servlet), servlet.getBaseUri() + "/*", null);
        }
        // -- gzip handler
        context.setGzipHandler(new GzipHandler());

        // -- the servlet
        ServletHolder servletHolder = new ServletHolder(theServlet);
        servletHolder.setInitParameters(ImmutableMap.copyOf(parameters));
        context.addServlet(servletHolder, "/*");

        // Starting with Jetty 9 there is no way to specify connectors directly, but
        // there is this wonky @ConnectorName virtual hosts automatically added
        String[] virtualHosts = new String[connectorNames.length];
        for (int i = 0; i < connectorNames.length; i++) {
            virtualHosts[i] = "@" + connectorNames[i];
        }
        context.setVirtualHosts(virtualHosts);

        return context;
    }

    private static SecurityHandler createSecurityHandler(LoginService loginService)
    {
        Constraint constraint = new Constraint();
        constraint.setAuthenticate(false);

        ConstraintMapping constraintMapping = new ConstraintMapping();
        constraintMapping.setConstraint(constraint);
        constraintMapping.setPathSpec("/*");

        ConstraintSecurityHandler securityHandler = new ConstraintSecurityHandler();
        securityHandler.setLoginService(loginService);

        // TODO: support for other auth schemes (digest, etc)
        securityHandler.setAuthenticator(new BasicAuthenticator());
        securityHandler.setConstraintMappings(ImmutableList.of(constraintMapping));
        return securityHandler;
    }

    private static DelimitedRequestLog createDelimitedRequestLog(HttpServerConfig config, TraceTokenManager tokenManager, EventClient eventClient)
            throws IOException
    {
        File logFile = new File(config.getLogPath());
        if (logFile.exists() && !logFile.isFile()) {
            throw new IOException(format("Log path %s exists but is not a file", logFile.getAbsolutePath()));
        }

        File logPath = logFile.getParentFile();
        if (!logPath.mkdirs() && !logPath.exists()) {
            throw new IOException(format("Cannot create %s and path does not already exist", logPath.getAbsolutePath()));
        }

        return new DelimitedRequestLog(
                config.getLogPath(),
                config.getLogHistory(),
                config.getLogQueueSize(),
                config.getLogMaxFileSize().toBytes(),
                tokenManager,
                eventClient,
                config.isLogCompressionEnabled());
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
    public int getLoggerQueueSize()
    {
        if (requestLog == null) {
            return 0;
        }
        return requestLog.getQueueSize();
    }

    @PostConstruct
    public void start()
            throws Exception
    {
        server.start();
        // clear the error handler registered by start()
        if (!registerErrorHandler) {
            server.setErrorHandler(null);
        }
        checkState(server.isStarted(), "server is not started");
    }

    @PreDestroy
    public void stop()
            throws Exception
    {
        server.setStopTimeout(0);
        server.stop();
        if (scheduledExecutorService != null) {
            scheduledExecutorService.shutdown();
        }
        if (requestLog != null) {
            requestLog.stop();
        }
    }

    @VisibleForTesting
    void join()
            throws InterruptedException
    {
        server.join();
    }

    private SslContextFactory.Server createReloadingSslContextFactory(HttpsConfig config, ClientCertificate clientCertificate, String environment)
    {
        if (scheduledExecutorService == null) {
            scheduledExecutorService = newSingleThreadScheduledExecutor(daemonThreadsNamed("HttpServerScheduler"));
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
}
