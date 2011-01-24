package com.proofpoint.platform.sample;

import com.proofpoint.bootstrap.Bootstrap;
import com.proofpoint.jersey.JerseyModule;
import com.proofpoint.jetty.JettyModule;
import com.proofpoint.jmx.JMXModule;
import org.weakref.jmx.guice.MBeanModule;

public class Main
{
    public static void main(String[] args)
            throws Exception
    {
        Bootstrap app = new Bootstrap(
                new JettyModule(),
                new JerseyModule(),
                new MBeanModule(),
                new JMXModule(),
                new MainModule());

        app.initialize();
    }
}