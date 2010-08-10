package com.proofpoint.jetty;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.servlet.GuiceFilter;
import org.apache.commons.lang.StringUtils;
import org.mortbay.jetty.Handler;
import org.mortbay.jetty.RequestLog;
import org.mortbay.jetty.Server;
import org.mortbay.jetty.handler.HandlerCollection;
import org.mortbay.jetty.handler.RequestLogHandler;
import org.mortbay.jetty.handler.StatisticsHandler;
import org.mortbay.jetty.nio.SelectChannelConnector;
import org.mortbay.jetty.security.BasicAuthenticator;
import org.mortbay.jetty.security.Constraint;
import org.mortbay.jetty.security.ConstraintMapping;
import org.mortbay.jetty.security.SecurityHandler;
import org.mortbay.jetty.security.SslSelectChannelConnector;
import org.mortbay.jetty.security.UserRealm;
import org.mortbay.jetty.servlet.Context;
import org.mortbay.jetty.servlet.DefaultServlet;
import org.mortbay.management.MBeanContainer;
import org.mortbay.servlet.GzipFilter;
import org.mortbay.thread.QueuedThreadPool;

import javax.annotation.Nullable;
import javax.management.MBeanServer;
import java.io.File;
import java.io.IOException;

/**
 * Provides an instance of a Jetty server ready to be configured with
 * com.google.inject.servlet.ServletModule
 */
public class JettyProvider
        implements Provider<Server>
{
    private MBeanServer mbeanServer;
    private JettyConfig config;
    private UserRealm realm;

    @Inject
    public JettyProvider(JettyConfig config)
    {
        this.config = config;
    }

    @Inject(optional = true)
    public void setMBeanServer(MBeanServer server)
    {
        mbeanServer = server;
    }

    @Inject(optional = true)
    public void setUserRealms(@Nullable UserRealm realm)
    {
        this.realm = realm;
    }

    public Server get()
    {
        String ip = config.getServerIp();
        if (StringUtils.equals(ip, "")) {
            ip = null;
        }
        
        Server server = new Server();

        if (mbeanServer != null) {
            // export jmx mbeans if a server was provided
            MBeanContainer mbeanContainer = new MBeanContainer(mbeanServer);
            server.getContainer().addEventListener(mbeanContainer);
        }

        // set up NIO-based HTTP connector
        SelectChannelConnector connector = new SelectChannelConnector();
        connector.setPort(config.getHttpPort());
        connector.setStatsOn(true);
        if (ip != null) {
            connector.setHost(ip);
        }
        
        server.addConnector(connector);

        if (config.isHttpsEnabled()) {
            SslSelectChannelConnector sslConnector = new SslSelectChannelConnector();
            sslConnector.setPort(config.getHttpsPort());
            sslConnector.setStatsOn(true);
            sslConnector.setKeystore(config.getKeystorePath());
            sslConnector.setPassword(config.getKeystorePassword());
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
        handlers.addHandler(getLogHandler());

        StatisticsHandler handler = new StatisticsHandler();
        handler.addHandler(handlers);
        server.addHandler(handler);

        return server;
    }

    private Context getContextHandler()
    {
        Context context = new Context(Context.DEFAULT);
        context.addFilter(GzipFilter.class, "/*", Handler.DEFAULT);
        context.addFilter(GZipRequestFilter.class, "/*", Handler.DEFAULT);
        context.addFilter(GuiceFilter.class, "/*", Handler.DEFAULT);
        context.addServlet(DefaultServlet.class, "/");

        if (realm != null) {
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

        SecurityHandler securityHandler = new SecurityHandler();
        securityHandler.setUserRealm(realm);

        // TODO: support for other auth schemes (digest, etc)
        securityHandler.setAuthenticator(new BasicAuthenticator());
        securityHandler.setConstraintMappings(new ConstraintMapping[]{constraintMapping});
        return securityHandler;
    }

    private RequestLogHandler getLogHandler()
    {
        // TODO: use custom (more easily-parseable) format
        // TODO: make retention & rotation configurable
        RequestLogHandler logHandler = new RequestLogHandler();

        File logPath = new File(config.getLogPath()).getParentFile();
        logPath.mkdirs();

        RequestLog requestLog;
        try {
            requestLog = new DelimitedRequestLog(config.getLogPath(), config.getLogRetainDays());
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
        
        logHandler.setRequestLog(requestLog);

        return logHandler;
    }
}
