package com.proofpoint.platform.sample;

import com.proofpoint.bootstrap.Bootstrap;
import com.proofpoint.jersey.JaxrsModule;
import com.proofpoint.http.server.HttpServerModule;
import com.proofpoint.jmx.JMXModule;
import org.weakref.jmx.guice.MBeanModule;

public class Main
{
    public static void main(String[] args)
            throws Exception
    {
        Bootstrap app = new Bootstrap(
                new HttpServerModule(),
                new JaxrsModule(),
                new MBeanModule(),
                new JMXModule(),
                new MainModule());

        app.initialize();
    }
}