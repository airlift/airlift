package com.proofpoint.http.server;

import com.google.inject.Inject;
import org.eclipse.jetty.server.Server;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

/**
 * Wraps the Jetty Server with Life Cycle annotations
 */
public class JettyServer
{
    private final Server server;

    @Inject
    public JettyServer(Server server)
    {
        this.server = server;
    }

    @PostConstruct
    public void start() throws Exception
    {
        server.start();
    }

    @PreDestroy
    public void stop() throws Exception
    {
        server.stop();
    }
}
