package com.proofpoint.http.server.testing;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.proofpoint.http.server.GZipRequestFilter;
import com.proofpoint.http.server.TheServlet;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.nio.SelectChannelConnector;
import org.eclipse.jetty.servlet.FilterMapping;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.servlets.GzipFilter;
import org.eclipse.jetty.util.thread.QueuedThreadPool;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.servlet.Servlet;
import java.net.URI;
import java.util.Map;

import static java.lang.String.format;

/**
 * HTTP server that binds to localhost on a random port
 */
public class TestingHttpServer
{
    private final Servlet servlet;
    private final Map<String, String> initParameters;
    private Server server;
    private SelectChannelConnector connector;

    @Inject
    public TestingHttpServer(@TheServlet Servlet servlet, @TheServlet Map<String, String> initParameters)
    {
        this.servlet = servlet;
        this.initParameters = ImmutableMap.copyOf(initParameters);

        initialize();
    }

    @PostConstruct
    public void start()
            throws Exception
    {
        server.start();
    }

    @PreDestroy
    public void stop()
            throws Exception
    {
        server.stop();
    }

    private void initialize()
    {
        server = new Server();

        connector = new SelectChannelConnector();
        connector.setHost("localhost");
        server.addConnector(connector);

        QueuedThreadPool threadPool = new QueuedThreadPool(2);
        threadPool.setMinThreads(1);
        server.setThreadPool(threadPool);
        server.setHandler(buildContextHandler());
    }

    private ServletContextHandler buildContextHandler()
    {
        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.NO_SESSIONS);
        context.addFilter(GzipFilter.class, "/*", FilterMapping.DEFAULT);
        context.addFilter(GZipRequestFilter.class, "/*", FilterMapping.DEFAULT);

        ServletHolder servletHolder = new ServletHolder(servlet);
        servletHolder.setInitParameters(initParameters);
        context.addServlet(servletHolder, "/*");

        return context;
    }

    public URI getBaseUrl()
    {
        return URI.create(format("http://localhost:%d/", getPort()));
    }

    public int getPort()
    {
        int port = connector.getLocalPort();

        if (port == -1) {
            throw new IllegalStateException("Server has not yet been started");
        }
        else if (port == -2) {
            throw new IllegalStateException("Server has already been stopped");
        }

        return port;
    }
}
