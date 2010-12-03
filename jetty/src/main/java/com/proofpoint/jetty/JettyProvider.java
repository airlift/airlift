package com.proofpoint.jetty;

import com.google.inject.Inject;
import com.google.inject.Provider;
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

import javax.annotation.Nullable;
import javax.management.MBeanServer;
import javax.servlet.Servlet;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.Collections;

import static java.lang.String.format;

/**
 * Provides an instance of a Jetty server ready to be configured with
 * com.google.inject.servlet.ServletModule
 */
public class JettyProvider
        implements Provider<Server>
{
    private MBeanServer mbeanServer;
    private JettyConfig config;
    private final Servlet theServlet;
    private LoginService loginService;
    private Map<String, String> servletInitParameters = Collections.emptyMap();

    @Inject
    public JettyProvider(JettyConfig config, @TheServlet Servlet theServlet)
    {
        this.config = config;
        this.theServlet = theServlet;
    }

    @Inject(optional = true)
    public void setServletInitParameters(@TheServlet Map<String, String> parameters)
    {
        this.servletInitParameters = parameters;
    }

    @Inject(optional = true)
    public void setMBeanServer(MBeanServer server)
    {
        mbeanServer = server;
    }

    @Inject(optional = true)
    public void setLoginService(@Nullable LoginService loginService)
    {
        this.loginService = loginService;
    }

    public Server get()
    {
        String ip = config.getServerIp();
        
        Server server = new Server();

        if (mbeanServer != null) {
            // export jmx mbeans if a server was provided
            MBeanContainer mbeanContainer = new MBeanContainer(mbeanServer);
            server.getContainer().addEventListener(mbeanContainer);
        }

        // set up NIO-based HTTP connector
        if (config.isHttpEnabled()) {
            SelectChannelConnector connector = new SelectChannelConnector();
            connector.setPort(config.getHttpPort());
            connector.setMaxIdleTime(config.getNetworkMaxIdleTime());
            connector.setStatsOn(true);
            if (ip != null) {
                connector.setHost(ip);
            }

            server.addConnector(connector);
        }

        if (config.isHttpsEnabled()) {
            SslSelectChannelConnector sslConnector = new SslSelectChannelConnector();
            sslConnector.setPort(config.getHttpsPort());
            sslConnector.setStatsOn(true);
            sslConnector.setKeystore(config.getKeystorePath());
            sslConnector.setPassword(config.getKeystorePassword());
            sslConnector.setMaxIdleTime(config.getNetworkMaxIdleTime());
            if (ip != null) {
                sslConnector.setHost(ip);
            }

            server.addConnector(sslConnector);
        }

        QueuedThreadPool threadpool = new QueuedThreadPool(config.getMaxThreads());
        threadpool.setMinThreads(config.getMinThreads());
        threadpool.setMaxIdleTimeMs(config.getThreadMaxIdleTime());
        server.setThreadPool(threadpool);

        /**
         * structure is:
         *
         * server
         *    |--- statistics handler
         *           |--- context handler
         *           |       |--- gzip response filter
         *           |       |--- gzip request filter
         *           |       |--- security handler
         *           |       |--- guice filter
         *           |       |--- default servlet (no op, as all requests are handled by filter)
         *           |--- log handler
         */
        HandlerCollection handlers = new HandlerCollection();
        handlers.addHandler(getContextHandler());
        try {
            handlers.addHandler(getLogHandler());
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }

        StatisticsHandler statsHandler = new StatisticsHandler();
        statsHandler.setHandler(handlers);
        server.setHandler(statsHandler);

        return server;
    }

    private ServletContextHandler getContextHandler()
    {
        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.NO_SESSIONS);
        context.addFilter(GzipFilter.class, "/*", FilterMapping.DEFAULT);
        context.addFilter(GZipRequestFilter.class, "/*", FilterMapping.DEFAULT);

        ServletHolder servletHolder = new ServletHolder(theServlet);
        servletHolder.setInitParameters(servletInitParameters);
        context.addServlet(servletHolder, "/*");

        if (loginService != null) {
            context.setSecurityHandler(getSecurityHandler());
        }

        return context;
    }

    private SecurityHandler getSecurityHandler()
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

    private RequestLogHandler getLogHandler()
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


        RequestLog requestLog = new DelimitedRequestLog(config.getLogPath(), config.getLogRetainDays());
        logHandler.setRequestLog(requestLog);

        return logHandler;
    }
}
