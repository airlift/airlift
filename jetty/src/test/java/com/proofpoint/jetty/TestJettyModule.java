package com.proofpoint.jetty;

import com.beust.jcommander.internal.Maps;
import com.google.inject.Binder;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.proofpoint.configuration.ConfigurationFactory;
import com.proofpoint.configuration.ConfigurationModule;
import org.eclipse.jetty.server.Server;
import org.testng.annotations.Test;

import javax.servlet.Servlet;
import java.util.Map;

public class TestJettyModule
{
    @Test
    public void testCanConstructServer()
    {
        Map<String, String> properties = Maps.newHashMap();
        ConfigurationFactory configFactory = new ConfigurationFactory(properties);
        Injector injector = Guice.createInjector(new JettyModule(),
                                                 new ConfigurationModule(configFactory),
                                                 new Module()
        {
            @Override
            public void configure(Binder binder)
            {
                binder.bind(Servlet.class).annotatedWith(TheServlet.class).to(DummyServlet.class);
            }
        });

        injector.getInstance(Server.class);
    }



}
