package com.proofpoint.jetty;

import com.beust.jcommander.internal.Maps;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Files;
import com.google.inject.Binder;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.proofpoint.configuration.ConfigurationFactory;
import com.proofpoint.configuration.ConfigurationModule;
import org.eclipse.jetty.server.Server;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.servlet.Servlet;
import java.io.File;
import java.io.IOException;
import java.util.Map;

public class TestJettyModule
{
    private File tempDir;

    @BeforeMethod
    public void setup()
            throws IOException
    {
        tempDir = Files.createTempDir().getCanonicalFile(); // getCanonicalFile needed to get around Issue 365 (http://code.google.com/p/guava-libraries/issues/detail?id=365)
    }

    @AfterMethod
    public void tearDown()
            throws IOException
    {
        Files.deleteRecursively(tempDir);
    }

    @Test
    public void testCanConstructServer()
            throws IOException
    {
        Map<String, String> properties = new ImmutableMap.Builder<String, String>()
                .put("jetty.log.path", new File(tempDir, "jetty.log").getAbsolutePath())
                .build();

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
