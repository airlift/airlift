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

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import io.airlift.event.client.EventClient;
import io.airlift.node.NodeInfo;
import io.airlift.tracetoken.TraceTokenManager;
import org.eclipse.jetty.jmx.MBeanContainer;
import org.eclipse.jetty.security.ConstraintMapping;
import org.eclipse.jetty.security.ConstraintSecurityHandler;
import org.eclipse.jetty.security.LoginService;
import org.eclipse.jetty.security.SecurityHandler;
import org.eclipse.jetty.security.authentication.BasicAuthenticator;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.RequestLog;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.HandlerCollection;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.server.handler.RequestLogHandler;
import org.eclipse.jetty.server.handler.StatisticsHandler;
import org.eclipse.jetty.server.nio.SelectChannelConnector;
import org.eclipse.jetty.server.ssl.SslSelectChannelConnector;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.servlets.GzipFilter;
import org.eclipse.jetty.util.security.Constraint;
import org.eclipse.jetty.util.thread.QueuedThreadPool;

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
import java.util.concurrent.TimeUnit;

import static com.google.common.base.Preconditions.checkState;
import static java.lang.String.format;

public class HttpServer
{
    private final Server server;
    private final Connector httpConnector;
    private final Connector httpsConnector;
    private final Connector adminConnector;

    @SuppressWarnings({"deprecation"})
    public HttpServer(HttpServerInfo httpServerInfo,
            NodeInfo nodeInfo,
            HttpServerConfig config,
            Servlet theServlet,
            Map<String, String> parameters,
            Set<Filter> filters,
            Servlet theAdminServlet,
            Map<String, String> adminParameters,
            Set<Filter> adminFilters,
            MBeanServer mbeanServer,
            LoginService loginService,
            TraceTokenManager tokenManager,
            RequestStats stats,
            EventClient eventClient)
            throws IOException
    {
        Preconditions.checkNotNull(httpServerInfo, "httpServerInfo is null");
        Preconditions.checkNotNull(nodeInfo, "nodeInfo is null");
        Preconditions.checkNotNull(config, "config is null");
        Preconditions.checkNotNull(theServlet, "theServlet is null");

        Server server = new Server();
        server.setSendServerVersion(false);

        if (mbeanServer != null) {
            // export jmx mbeans if a server was provided
            MBeanContainer mbeanContainer = new MBeanContainer(mbeanServer)
            {
                @Override
                public void doStart()
                {
                    // jetty registers a shutdown hook that can cause a deadlock
                }
            };
            server.getContainer().addEventListener(mbeanContainer);
        }

        // set up NIO-based HTTP connector
        SelectChannelConnector httpConnector = null;
        if (config.isHttpEnabled()) {
            httpConnector = new SelectChannelConnector();
            httpConnector.setName("http");
            httpConnector.setPort(httpServerInfo.getHttpUri().getPort());
            httpConnector.setMaxIdleTime((int) config.getNetworkMaxIdleTime().convertTo(TimeUnit.MILLISECONDS));
            httpConnector.setStatsOn(true);
            httpConnector.setHost(nodeInfo.getBindIp().getHostAddress());

            server.addConnector(httpConnector);
        }

        // set up NIO-based HTTPS connector
        SslSelectChannelConnector httpsConnector = null;
        if (config.isHttpsEnabled()) {
            httpsConnector = new SslSelectChannelConnector();
            httpsConnector.setName("https");
            httpsConnector.setPort(httpServerInfo.getHttpsUri().getPort());
            httpsConnector.setStatsOn(true);
            httpsConnector.setKeystore(config.getKeystorePath());
            httpsConnector.setPassword(config.getKeystorePassword());
            httpsConnector.setMaxIdleTime((int) config.getNetworkMaxIdleTime().convertTo(TimeUnit.MILLISECONDS));
            httpsConnector.setHost(nodeInfo.getBindIp().getHostAddress());
            httpsConnector.setAllowRenegotiate(true);

            server.addConnector(httpsConnector);
        }

        // set up NIO-based Admin connector
        SelectChannelConnector adminConnector = null;
        if (theAdminServlet != null && config.isAdminEnabled()) {
            if (config.isHttpsEnabled()) {
                SslSelectChannelConnector connector = new SslSelectChannelConnector();
                connector.setKeystore(config.getKeystorePath());
                connector.setPassword(config.getKeystorePassword());
                connector.setAllowRenegotiate(true);
                adminConnector = connector;
            }
            else {
                adminConnector = new SelectChannelConnector();
            }
            adminConnector.setName("admin");
            adminConnector.setPort(httpServerInfo.getAdminUri().getPort());
            adminConnector.setMaxIdleTime((int) config.getNetworkMaxIdleTime().convertTo(TimeUnit.MILLISECONDS));
            adminConnector.setStatsOn(true);
            adminConnector.setHost(nodeInfo.getBindIp().getHostAddress());

            QueuedThreadPool adminThreadPool = new QueuedThreadPool(config.getAdminMaxThreads());
            adminThreadPool.setName("http-admin-worker");
            adminThreadPool.setMinThreads(config.getAdminMinThreads());
            adminThreadPool.setMaxIdleTimeMs((int) config.getThreadMaxIdleTime().convertTo(TimeUnit.MILLISECONDS));
            adminConnector.setThreadPool(adminThreadPool);

            server.addBean(adminThreadPool); // workaround until jetty bug 373272 is fixed
            server.addConnector(adminConnector);
        }

        QueuedThreadPool threadPool = new QueuedThreadPool(config.getMaxThreads());
        threadPool.setMinThreads(config.getMinThreads());
        threadPool.setMaxIdleTimeMs((int) config.getThreadMaxIdleTime().convertTo(TimeUnit.MILLISECONDS));
        threadPool.setName("http-worker");
        server.setThreadPool(threadPool);

        /**
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
         *           |--- log handler
         *    |-- admin context handler
         *           \ --- the admin servlet
         */
        HandlerCollection handlers = new HandlerCollection();
        handlers.addHandler(createServletContext(theServlet, parameters, filters, tokenManager, loginService, "http", "https"));
        RequestLogHandler logHandler = createLogHandler(config, tokenManager, eventClient);
        if (logHandler != null) {
            handlers.addHandler(logHandler);
        }

        RequestLogHandler statsRecorder = new RequestLogHandler();
        statsRecorder.setRequestLog(new StatsRecordingHandler(stats));
        handlers.addHandler(statsRecorder);

        // add handlers to Jetty
        StatisticsHandler statsHandler = new StatisticsHandler();
        statsHandler.setHandler(handlers);

        HandlerList rootHandlers = new HandlerList();
        if (theAdminServlet != null && config.isAdminEnabled()) {
            rootHandlers.addHandler(createServletContext(theAdminServlet, adminParameters, adminFilters, tokenManager, loginService, "admin"));
        }
        rootHandlers.addHandler(statsHandler);
        server.setHandler(rootHandlers);

        this.server = server;
        this.httpConnector = httpConnector;
        this.httpsConnector = httpsConnector;
        this.adminConnector = adminConnector;
    }

    private static ServletContextHandler createServletContext(Servlet theServlet,
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

        // -- gzip response filter
        context.addFilter(GzipFilter.class, "/*", null);
        // -- gzip request filter
        context.addFilter(GZipRequestFilter.class, "/*", null);
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
        context.setConnectorNames(connectorNames);
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

    protected RequestLogHandler createLogHandler(HttpServerConfig config, TraceTokenManager tokenManager, EventClient eventClient)
            throws IOException
    {
        // TODO: use custom (more easily-parseable) format
        // TODO: make retention & rotation configurable
        RequestLogHandler logHandler = new RequestLogHandler();

        File logFile = new File(config.getLogPath());
        if (logFile.exists() && !logFile.isFile()) {
            throw new IOException(format("Log path %s exists but is not a file", logFile.getAbsolutePath()));
        }

        File logPath = logFile.getParentFile();
        if (!logPath.mkdirs() && !logPath.exists()) {
            throw new IOException(format("Cannot create %s and path does not already exist", logPath.getAbsolutePath()));
        }

        RequestLog requestLog = new DelimitedRequestLog(config.getLogPath(), (int) config.getLogRetentionTime().convertTo(TimeUnit.DAYS), tokenManager, eventClient);
        logHandler.setRequestLog(requestLog);

        return logHandler;
    }

    @PostConstruct
    public void start()
            throws Exception
    {
        server.start();
        checkState(server.isRunning(), "server is not running");

        // The combination of an NIO connector and an insufficient number of threads results
        // in a server that hangs after accepting connections. Jetty scales the number of
        // required threads based on the number of available processors in a non-trivial way,
        // so a config that works on one machine might fail on a larger machine without an
        // obvious reason why. Thus, we need this runtime check after startup as a safeguard.
        checkSufficientThreads(httpConnector, "HTTP");
        checkSufficientThreads(httpsConnector, "HTTPS");
        checkSufficientThreads(adminConnector, "admin");
    }

    @PreDestroy
    public void stop()
            throws Exception
    {
        server.stop();
    }

    private static void checkSufficientThreads(Connector connector, String name)
    {
        checkState((connector == null) || !connector.isLowResources(), "insufficient threads configured for %s connector", name);
    }
}
