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

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.proofpoint.node.NodeInfo;
import org.eclipse.jetty.http.security.Constraint;
import org.eclipse.jetty.jmx.MBeanContainer;
import org.eclipse.jetty.security.ConstraintMapping;
import org.eclipse.jetty.security.ConstraintSecurityHandler;
import org.eclipse.jetty.security.LoginService;
import org.eclipse.jetty.security.SecurityHandler;
import org.eclipse.jetty.security.authentication.BasicAuthenticator;
import org.eclipse.jetty.server.RequestLog;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.HandlerCollection;
import org.eclipse.jetty.server.handler.RequestLogHandler;
import org.eclipse.jetty.server.handler.StatisticsHandler;
import org.eclipse.jetty.server.nio.SelectChannelConnector;
import org.eclipse.jetty.server.ssl.SslSelectChannelConnector;
import org.eclipse.jetty.servlet.FilterMapping;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.servlets.GzipFilter;
import org.eclipse.jetty.util.thread.QueuedThreadPool;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.management.MBeanServer;
import javax.servlet.Servlet;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static java.lang.String.format;

public class HttpServer
{
    private final Server server;

    public HttpServer(HttpServerInfo httpServerInfo, NodeInfo nodeInfo, HttpServerConfig config, Servlet theServlet, Map<String, String> parameters, MBeanServer mbeanServer, LoginService loginService)
            throws IOException
    {
        Preconditions.checkNotNull(httpServerInfo, "httpServerInfo is null");
        Preconditions.checkNotNull(nodeInfo, "nodeInfo is null");
        Preconditions.checkNotNull(config, "config is null");
        Preconditions.checkNotNull(theServlet, "theServlet is null");

        Server server = new Server();

        if (mbeanServer != null) {
            // export jmx mbeans if a server was provided
            MBeanContainer mbeanContainer = new MBeanContainer(mbeanServer) {
                @Override
                public void doStart()
                {
                    // jetty registers a shutdown hook that can cause a deadlock
                }
            };
            server.getContainer().addEventListener(mbeanContainer);
        }

        // set up NIO-based HTTP connector
        SelectChannelConnector httpConnector;
        if (config.isHttpEnabled()) {
            httpConnector = new SelectChannelConnector();
            httpConnector.setPort(httpServerInfo.getHttpUri().getPort());
            httpConnector.setMaxIdleTime((int) config.getNetworkMaxIdleTime().convertTo(TimeUnit.MILLISECONDS));
            httpConnector.setStatsOn(true);
            httpConnector.setHost(nodeInfo.getBindIp().getHostAddress());

            server.addConnector(httpConnector);
        }

        // set up NIO-based HTTPS connector
        SslSelectChannelConnector httpsConnector;
        if (config.isHttpsEnabled()) {
            httpsConnector = new SslSelectChannelConnector();
            httpsConnector.setPort(httpServerInfo.getHttpsUri().getPort());
            httpsConnector.setStatsOn(true);
            httpsConnector.setKeystore(config.getKeystorePath());
            httpsConnector.setPassword(config.getKeystorePassword());
            httpsConnector.setMaxIdleTime((int) config.getNetworkMaxIdleTime().convertTo(TimeUnit.MILLISECONDS));
            httpsConnector.setHost(nodeInfo.getBindIp().getHostAddress());

            server.addConnector(httpsConnector);
        }

        QueuedThreadPool threadPool = new QueuedThreadPool(config.getMaxThreads());
        threadPool.setMinThreads(config.getMinThreads());
        threadPool.setMaxIdleTimeMs((int) config.getThreadMaxIdleTime().convertTo(TimeUnit.MILLISECONDS));
        server.setThreadPool(threadPool);

        /**
         * structure is:
         *
         * server
         *    |--- statistics handler
         *           |--- context handler
         *           |       |--- gzip response filter
         *           |       |--- gzip request filter
         *           |       |--- security handler
         *           |       |--- the servlet (normally GuiceContainer)
         *           |--- log handler
         */
        HandlerCollection handlers = new HandlerCollection();
        handlers.addHandler(createServletContext(theServlet, parameters, loginService));
        RequestLogHandler logHandler = createLogHandler(config);
        if (logHandler != null) {
            handlers.addHandler(logHandler);
        }

        // add handlers to Jetty
        StatisticsHandler statsHandler = new StatisticsHandler();
        statsHandler.setHandler(handlers);
        server.setHandler(statsHandler);

        this.server = server;
    }

    private static ServletContextHandler createServletContext(Servlet theServlet, Map<String, String> parameters, LoginService loginService)
    {
        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.NO_SESSIONS);
        // -- gzip response filter
        context.addFilter(GzipFilter.class, "/*", FilterMapping.DEFAULT);
        // -- gzip request filter
        context.addFilter(GZipRequestFilter.class, "/*", FilterMapping.DEFAULT);
        // -- security handler
        if (loginService != null) {
            SecurityHandler securityHandler = createSecurityHandler(loginService);
            context.setSecurityHandler(securityHandler);
        }
        // -- the servlet
        ServletHolder servletHolder = new ServletHolder(theServlet);
        servletHolder.setInitParameters(ImmutableMap.copyOf(parameters));
        context.addServlet(servletHolder, "/*");
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

    protected RequestLogHandler createLogHandler(HttpServerConfig config)
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


        RequestLog requestLog = new DelimitedRequestLog(config.getLogPath(), (int) config.getLogRetentionTime().convertTo(TimeUnit.DAYS));
        logHandler.setRequestLog(requestLog);

        return logHandler;
    }

    @PostConstruct
    public void start()
            throws Exception
    {
        server.start();
        Preconditions.checkState(server.isRunning(), "server is not running");
    }

    @PreDestroy
    public void stop()
            throws Exception
    {
        server.stop();
    }
}
