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
package com.proofpoint.http.server;

import com.google.common.collect.ImmutableMap;
import com.google.common.primitives.Ints;
import com.proofpoint.http.server.HttpServerBinder.HttpResourceBinding;
import com.proofpoint.node.NodeInfo;
import com.proofpoint.tracetoken.TraceTokenManager;
import org.eclipse.jetty.jmx.MBeanContainer;
import org.eclipse.jetty.security.ConstraintMapping;
import org.eclipse.jetty.security.ConstraintSecurityHandler;
import org.eclipse.jetty.security.LoginService;
import org.eclipse.jetty.security.SecurityHandler;
import org.eclipse.jetty.security.authentication.BasicAuthenticator;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.RequestLog;
import org.eclipse.jetty.server.SecureRequestCustomizer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.SslConnectionFactory;
import org.eclipse.jetty.server.handler.HandlerCollection;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.server.handler.RequestLogHandler;
import org.eclipse.jetty.server.handler.StatisticsHandler;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.servlets.GzipFilter;
import org.eclipse.jetty.util.security.Constraint;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.eclipse.jetty.util.thread.ThreadPool;
import org.weakref.jmx.Flatten;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.management.MBeanServer;
import javax.servlet.Filter;
import javax.servlet.Servlet;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static java.lang.String.format;

public class HttpServer
{
    private final Server server;
    private final ServerConnector httpConnector;
    private final ServerConnector httpsConnector;
    private final ServerConnector adminConnector;
    private final RequestStats stats;

    @SuppressWarnings({"deprecation"})
    public HttpServer(HttpServerInfo httpServerInfo,
            NodeInfo nodeInfo,
            HttpServerConfig config,
            Servlet theServlet,
            Map<String, String> parameters,
            Set<Filter> filters,
            Set<HttpResourceBinding> resources,
            Map<String, String> adminParameters,
            Set<Filter> adminFilters,
            MBeanServer mbeanServer,
            LoginService loginService,
            QueryStringFilter queryStringFilter,
            TraceTokenManager tokenManager,
            RequestStats stats,
            DetailedRequestStats detailedRequestStats)
            throws IOException
    {
        checkNotNull(httpServerInfo, "httpServerInfo is null");
        checkNotNull(nodeInfo, "nodeInfo is null");
        checkNotNull(config, "config is null");
        checkNotNull(queryStringFilter, "queryStringFilter is null");
        checkNotNull(theServlet, "theServlet is null");

        QueuedThreadPool threadPool = new QueuedThreadPool(config.getMaxThreads());
        threadPool.setMinThreads(config.getMinThreads());
        threadPool.setIdleTimeout(Ints.checkedCast(config.getThreadMaxIdleTime().toMillis()));
        threadPool.setName("http-worker");
        server = new Server(threadPool);
        this.stats = stats;

        if (mbeanServer != null) {
            // export jmx mbeans if a server was provided
            MBeanContainer mbeanContainer = new MBeanContainer(mbeanServer);
            server.addBean(mbeanContainer);
        }

        // set up HTTP connector
        if (config.isHttpEnabled()) {
            HttpConfiguration httpConfiguration = new HttpConfiguration();
            httpConfiguration.setSendServerVersion(false);
            httpConfiguration.setSendXPoweredBy(false);
            if (config.getMaxRequestHeaderSize() != null) {
                httpConfiguration.setRequestHeaderSize(Ints.checkedCast(config.getMaxRequestHeaderSize().toBytes()));
            }

            // if https is enabled, set the CONFIDENTIAL and INTEGRAL redirection information
            if (config.isHttpsEnabled()) {
                httpConfiguration.setSecureScheme("https");
                httpConfiguration.setSecurePort(httpServerInfo.getHttpsUri().getPort());
            }

            httpConnector = new ServerConnector(server, new HttpConnectionFactory(httpConfiguration));
            httpConnector.setName("http");
            httpConnector.setPort(httpServerInfo.getHttpUri().getPort());
            httpConnector.setIdleTimeout(config.getNetworkMaxIdleTime().toMillis());
            httpConnector.setHost(nodeInfo.getBindIp().getHostAddress());
            server.addConnector(httpConnector);
        } else {
            httpConnector = null;
        }

        // set up NIO-based HTTPS connector
        if (config.isHttpsEnabled()) {
            HttpConfiguration httpsConfiguration = new HttpConfiguration();
            httpsConfiguration.setSendServerVersion(false);
            httpsConfiguration.setSendXPoweredBy(false);
            if (config.getMaxRequestHeaderSize() != null) {
                httpsConfiguration.setRequestHeaderSize(Ints.checkedCast(config.getMaxRequestHeaderSize().toBytes()));
            }
            httpsConfiguration.addCustomizer(new SecureRequestCustomizer());

            SslContextFactory sslContextFactory = new SslContextFactory(config.getKeystorePath());
            sslContextFactory.setKeyStorePassword(config.getKeystorePassword());
            SslConnectionFactory sslConnectionFactory = new SslConnectionFactory(sslContextFactory, "http/1.1");

            httpsConnector = new ServerConnector(server, sslConnectionFactory, new HttpConnectionFactory(httpsConfiguration));
            httpsConnector.setName("https");
            httpsConnector.setPort(httpServerInfo.getHttpsUri().getPort());
            httpsConnector.setIdleTimeout(config.getNetworkMaxIdleTime().toMillis());
            httpsConnector.setHost(nodeInfo.getBindIp().getHostAddress());

            server.addConnector(httpsConnector);
        } else {
            httpsConnector = null;
        }

        // set up NIO-based Admin connector
        if (config.isAdminEnabled()) {
            HttpConfiguration adminConfiguration = new HttpConfiguration();
            adminConfiguration.setSendServerVersion(false);
            adminConfiguration.setSendXPoweredBy(false);
            if (config.getMaxRequestHeaderSize() != null) {
                adminConfiguration.setRequestHeaderSize(Ints.checkedCast(config.getMaxRequestHeaderSize().toBytes()));
            }

            QueuedThreadPool adminThreadPool = new QueuedThreadPool(config.getAdminMaxThreads());
            adminThreadPool.setName("http-admin-worker");
            adminThreadPool.setMinThreads(config.getAdminMinThreads());
            adminThreadPool.setIdleTimeout(Ints.checkedCast(config.getThreadMaxIdleTime().toMillis()));

            if (config.isHttpsEnabled()) {
                adminConfiguration.addCustomizer(new SecureRequestCustomizer());

                SslContextFactory sslContextFactory = new SslContextFactory(config.getKeystorePath());
                sslContextFactory.setKeyStorePassword(config.getKeystorePassword());
                SslConnectionFactory sslConnectionFactory = new SslConnectionFactory(sslContextFactory, "http/1.1");
                adminConnector = new ServerConnector(server, adminThreadPool, null, null, 0, -1, sslConnectionFactory, new HttpConnectionFactory(adminConfiguration));
            } else {
                adminConnector = new ServerConnector(server, adminThreadPool, null, null, 0, -1, new HttpConnectionFactory(adminConfiguration));
            }

            adminConnector.setName("admin");
            adminConnector.setPort(httpServerInfo.getAdminUri().getPort());
            adminConnector.setIdleTimeout(config.getNetworkMaxIdleTime().toMillis());
            adminConnector.setHost(nodeInfo.getBindIp().getHostAddress());

            server.addConnector(adminConnector);
        } else {
            adminConnector = null;
        }

        /**
         * structure is:
         *
         * server
         *    |--- statistics handler
         *           |--- context handler
         *           |       |--- (no) admin filter
         *           |       |--- timing filter
         *           |       |--- query string filter
         *           |       |--- trace token filter
         *           |       |--- gzip response filter
         *           |       |--- gzip request filter
         *           |       |--- security handler
         *           |       |--- user provided filters
         *           |       |--- the servlet (normally GuiceContainer)
         *           |       |--- resource handlers
         *           |--- log handler
         *    |-- admin context handler
         *           |--- admin filter
         *           |--- timing filter
         *           |--- query string filter
         *           |--- trace token filter
         *           |--- gzip response filter
         *           |--- gzip request filter
         *           |--- security handler
         *           |--- user provided admin filters
         *           \--- the servlet
         */
        HandlerCollection handlers = new HandlerCollection();

        for (HttpResourceBinding resource : resources) {
            handlers.addHandler(new ClassPathResourceHandler(resource.getBaseUri(), resource.getClassPathResourceBase(), resource.getWelcomeFiles()));
        }

        handlers.addHandler(createServletContext(theServlet, parameters, false, filters, queryStringFilter, tokenManager, loginService, "http", "https"));
        RequestLogHandler logHandler = createLogHandler(config, tokenManager);
        if (logHandler != null) {
            handlers.addHandler(logHandler);
        }

        RequestLogHandler statsRecorder = new RequestLogHandler();
        statsRecorder.setRequestLog(new StatsRecordingHandler(stats, detailedRequestStats));
        handlers.addHandler(statsRecorder);

        // add handlers to Jetty
        StatisticsHandler statsHandler = new StatisticsHandler();
        statsHandler.setHandler(handlers);

        HandlerList rootHandlers = new HandlerList();
        if (config.isAdminEnabled()) {
            rootHandlers.addHandler(createServletContext(theServlet, adminParameters, true, adminFilters, queryStringFilter, tokenManager, loginService, "admin"));
        }
        rootHandlers.addHandler(statsHandler);
        server.setHandler(rootHandlers);
    }

    private static ServletContextHandler createServletContext(Servlet theServlet,
            Map<String, String> parameters,
            boolean isAdmin,
            Set<Filter> filters,
            QueryStringFilter queryStringFilter,
            TraceTokenManager tokenManager,
            LoginService loginService,
            String... connectorNames)
    {
        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.NO_SESSIONS);

        context.addFilter(new FilterHolder(new AdminFilter(isAdmin)), "/*", null);
        context.addFilter(new FilterHolder(new TimingFilter()), "/*", null);
        context.addFilter(new FilterHolder(queryStringFilter), "/*", null);
        if (tokenManager != null) {
            context.addFilter(new FilterHolder(new TraceTokenFilter(tokenManager)), "/*", null);
        }

        // -- gzip response filter
        context.addFilter(GzipFilter.class, "/*", null);
        // -- security handler
        if (loginService != null) {
            SecurityHandler securityHandler = createSecurityHandler(loginService);
            context.setSecurityHandler(securityHandler);
        }
        // -- user provided filters
        for (Filter filter : filters) {
            context.addFilter(new FilterHolder(filter), "/*", null);
        }
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
        securityHandler.setConstraintMappings(Arrays.asList(constraintMapping));
        return securityHandler;
    }

    protected RequestLogHandler createLogHandler(HttpServerConfig config, TraceTokenManager tokenManager)
            throws IOException
    {
        // TODO: use custom (more easily-parseable) format
        RequestLogHandler logHandler = new RequestLogHandler();

        File logFile = new File(config.getLogPath());
        if (logFile.exists() && !logFile.isFile()) {
            throw new IOException(format("Log path %s exists but is not a file", logFile.getAbsolutePath()));
        }

        File logPath = logFile.getParentFile();
        if (!logPath.mkdirs() && !logPath.exists()) {
            throw new IOException(format("Cannot create %s and path does not already exist", logPath.getAbsolutePath()));
        }

        RequestLog requestLog = new DelimitedRequestLog(config.getLogPath(), config.getLogMaxHistory(), config.getLogMaxSegmentSize().toBytes(), tokenManager);
        logHandler.setRequestLog(requestLog);

        return logHandler;
    }

    @PostConstruct
    public void start()
            throws Exception
    {
        server.start();
        checkState(server.isStarted(), "server is not started");

        // The combination of an NIO connector and an insufficient number of threads results
        // in a server that hangs after accepting connections. Jetty scales the number of
        // required threads based on the number of available processors in a non-trivial way,
        // so a config that works on one machine might fail on a larger machine without an
        // obvious reason why. Thus, we need this runtime check after startup as a safeguard.
        checkSufficientThreads(httpConnector, "HTTP");
        checkSufficientThreads(httpsConnector, "HTTPS");
        checkSufficientThreads(adminConnector, "admin");
        checkState(!server.getThreadPool().isLowOnThreads(), "insufficient threads configured for server connector");
    }

    @PreDestroy
    public void stop()
            throws Exception
    {
        server.stop();
    }

    @Flatten
    public RequestStats getStats()
    {
        return stats;
    }

    private static void checkSufficientThreads(Connector connector, String name)
    {
        if (connector == null) {
            return;
        }
        Executor executor = connector.getExecutor();
        if (executor instanceof ThreadPool) {
            ThreadPool queuedThreadPool = (ThreadPool) executor;
            checkState(!queuedThreadPool.isLowOnThreads(), "insufficient threads configured for %s connector", name);
        }

    }
}
