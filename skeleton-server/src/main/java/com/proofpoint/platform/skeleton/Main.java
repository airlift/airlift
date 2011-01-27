package com.proofpoint.platform.skeleton;

import com.proofpoint.bootstrap.Bootstrap;
import com.proofpoint.http.server.HttpServerModule;
import com.proofpoint.jaxrs.JaxrsModule;
import com.proofpoint.jmx.JmxModule;
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
                new JmxModule(),
                new MainModule());

        app.initialize();
    }
}