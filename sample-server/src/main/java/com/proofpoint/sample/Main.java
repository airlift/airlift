package com.proofpoint.sample;

import com.google.inject.Injector;
import com.proofpoint.bootstrap.Bootstrap;
import com.proofpoint.jersey.JerseyModule;
import com.proofpoint.jetty.JettyModule;
import com.proofpoint.jmx.JMXModule;
import org.eclipse.jetty.server.Server;

public class Main
{
    public static void main(String[] args)
            throws Exception
    {
        Bootstrap app = new Bootstrap(new JettyModule(),
                new JerseyModule(),
                new JMXModule(),
                new MainModule());

        Injector injector = app.initialize();
    }
}