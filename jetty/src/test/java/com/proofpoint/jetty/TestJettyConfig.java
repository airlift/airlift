package com.proofpoint.jetty;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.inject.Binder;
import com.google.inject.Guice;
import com.google.inject.Module;
import com.proofpoint.configuration.ConfigurationFactory;
import com.proofpoint.configuration.ConfigurationModule;
import com.proofpoint.stats.Duration;
import org.testng.annotations.Test;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

public class TestJettyConfig
{
    @Test
    public void testDefaults()
    {
        assertDefaults(new JettyConfig());
    }

    @Test
    public void testDefaultPropertyMappings()
    {
        Map<String, String> properties = Maps.newHashMap();
        JettyConfig config = buildConfig(properties);
        assertDefaults(config);
    }

    @Test
    public void testExplicitPropertyMappings()
    {
        Map<String, String> properties = new ImmutableMap.Builder<String, String>()
                .put("jetty.ip", "1.2.3.4")
                .put("jetty.http.enabled", "false")
                .put("jetty.http.port", "1")
                .put("jetty.https.enabled", "true")
                .put("jetty.https.port", "2")
                .put("jetty.https.keystore.path", "/keystore")
                .put("jetty.https.keystore.password", "keystore password")
                .put("jetty.log.path", "/log")
                .put("jetty.log.retention-time", "1d")
                .put("jetty.threads.min", "100")
                .put("jetty.threads.max", "500")
                .put("jetty.threads.max-idle-time", "10m")
                .put("jetty.net.max-idle-time", "20m")
                .put("jetty.auth.users-file", "/auth")
                .build();

        JettyConfig config = buildConfig(properties);

        assertEquals(config.getIp(), "1.2.3.4");
        assertFalse(config.isHttpEnabled());
        assertEquals(config.getHttpPort(), 1);
        assertTrue(config.isHttpsEnabled());
        assertEquals(config.getHttpsPort(), 2);
        assertEquals(config.getKeystorePath(), "/keystore");
        assertEquals(config.getKeystorePassword(), "keystore password");
        assertEquals(config.getLogPath(), "/log");
        assertEquals(config.getLogRetentionTime(), new Duration(1, TimeUnit.DAYS));
        assertEquals(config.getMinThreads(), 100);
        assertEquals(config.getMaxThreads(), 500);
        assertEquals(config.getThreadMaxIdleTime(), new Duration(10, TimeUnit.MINUTES));
        assertEquals(config.getNetworkMaxIdleTime(), new Duration(20, TimeUnit.MINUTES));
        assertEquals(config.getUserAuthFile(), "/auth");
    }

    @Test
    public void testLegacyProperties()
    {
        Map<String, String> properties = new ImmutableMap.Builder<String, String>()
                .put("jetty.log.retain-days", "5")
                .put("jetty.threads.max-idle-time-ms", "100")
                .put("jetty.net.max-idle-time-ms", "200")
                .build();

        JettyConfig config = buildConfig(properties);

        assertEquals(config.getLogRetentionTime(), new Duration(5, TimeUnit.DAYS));
        assertEquals(config.getThreadMaxIdleTime(), new Duration(100, TimeUnit.MILLISECONDS));
        assertEquals(config.getNetworkMaxIdleTime(), new Duration(200, TimeUnit.MILLISECONDS));
    }

    private JettyConfig buildConfig(Map<String, String> properties)
    {
        Module module = new Module()
        {
            @Override
            public void configure(Binder binder)
            {
                ConfigurationModule.bindConfig(binder).to(JettyConfig.class);
            }
        };

        return Guice.createInjector(new ConfigurationModule(new ConfigurationFactory(properties)), module)
                .getInstance(JettyConfig.class);
    }

    private void assertDefaults(JettyConfig config)
    {
        assertNull(config.getIp());
        assertTrue(config.isHttpEnabled());
        assertEquals(config.getHttpPort(), 8080);
        assertFalse(config.isHttpsEnabled());
        assertEquals(config.getHttpsPort(), 8443);
        assertNull(config.getKeystorePath());
        assertNull(config.getKeystorePassword());
        assertEquals(config.getLogPath(), "var/log/jetty.log");
        assertEquals(config.getLogRetentionTime(), new Duration(90, TimeUnit.DAYS));
        assertEquals(config.getMinThreads(), 2);
        assertEquals(config.getMaxThreads(), 200);
        assertEquals(config.getThreadMaxIdleTime(), new Duration(1, TimeUnit.MINUTES));
        assertEquals(config.getNetworkMaxIdleTime(), new Duration(200, TimeUnit.SECONDS));
        assertNull(config.getUserAuthFile());
    }

}
