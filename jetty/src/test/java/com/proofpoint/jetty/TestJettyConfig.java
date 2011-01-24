package com.proofpoint.jetty;

import com.google.common.collect.ImmutableMap;
import com.proofpoint.configuration.test.ConfigAssertions;
import com.proofpoint.units.Duration;
import org.testng.annotations.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class TestJettyConfig
{
    @Test
    public void testDefaults()
    {
        Map<String, Object> expectedAttributeValues = new HashMap<String, Object>();
        expectedAttributeValues.put("Ip", null);
        expectedAttributeValues.put("HttpEnabled", true);
        expectedAttributeValues.put("HttpPort", 8080);
        expectedAttributeValues.put("HttpsEnabled", false);
        expectedAttributeValues.put("HttpsPort", 8443);
        expectedAttributeValues.put("KeystorePath", null);
        expectedAttributeValues.put("KeystorePassword", null);
        expectedAttributeValues.put("LogPath", "var/log/jetty.log");
        expectedAttributeValues.put("LogRetentionTime", new Duration(90, TimeUnit.DAYS));
        expectedAttributeValues.put("MinThreads", 2);
        expectedAttributeValues.put("MaxThreads", 200);
        expectedAttributeValues.put("ThreadMaxIdleTime", new Duration(1, TimeUnit.MINUTES));
        expectedAttributeValues.put("NetworkMaxIdleTime", new Duration(200, TimeUnit.SECONDS));
        expectedAttributeValues.put("UserAuthFile", null);

        ConfigAssertions.assertDefaults(expectedAttributeValues, JettyConfig.class);
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

        JettyConfig expected = new JettyConfig()
                .setIp("1.2.3.4")
                .setHttpEnabled(false)
                .setHttpPort(1)
                .setHttpsEnabled(true)
                .setHttpsPort(2)
                .setKeystorePath("/keystore")
                .setKeystorePassword("keystore password")
                .setLogPath("/log")
                .setLogRetentionTime(new Duration(1, TimeUnit.DAYS))
                .setMinThreads(100)
                .setMaxThreads(500)
                .setThreadMaxIdleTime(new Duration(10, TimeUnit.MINUTES))
                .setNetworkMaxIdleTime(new Duration(20, TimeUnit.MINUTES))
                .setUserAuthFile("/auth");

        ConfigAssertions.assertFullMapping(properties, expected);
    }

    @Test
    public void testDeprecatedProperties()
    {
        Map<String, String> currentProperties = new ImmutableMap.Builder<String, String>()
                .put("jetty.log.retention-time", "1d")
                .put("jetty.threads.max-idle-time", "10m")
                .put("jetty.net.max-idle-time", "20m")
                .build();

        Map<String, String> oldProperties = new ImmutableMap.Builder<String, String>()
                .put("jetty.log.retain-days", "1")
                .put("jetty.threads.max-idle-time-ms", "600000")
                .put("jetty.net.max-idle-time-ms", "1200000")
                .build();

        ConfigAssertions.assertDeprecatedEquivalence(JettyConfig.class, currentProperties, oldProperties);
    }
}
