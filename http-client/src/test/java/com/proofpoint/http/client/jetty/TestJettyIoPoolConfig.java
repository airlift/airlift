package com.proofpoint.http.client.jetty;

import com.google.common.collect.ImmutableMap;
import org.testng.annotations.Test;

import java.util.Map;

import static com.proofpoint.configuration.testing.ConfigAssertions.assertFullMapping;
import static com.proofpoint.configuration.testing.ConfigAssertions.assertLegacyEquivalence;
import static com.proofpoint.configuration.testing.ConfigAssertions.assertRecordedDefaults;
import static com.proofpoint.configuration.testing.ConfigAssertions.recordDefaults;

public class TestJettyIoPoolConfig
{
    @Test
    public void testDefaults()
    {
        assertRecordedDefaults(recordDefaults(JettyIoPoolConfig.class)
                .setMaxThreads(200)
                .setMinThreads(8));
    }

    @Test
    public void testExplicitPropertyMappings()
    {
        Map<String, String> properties = new ImmutableMap.Builder<String, String>()
                .put("http-client.max-threads", "33")
                .put("http-client.min-threads", "11")
                .build();

        JettyIoPoolConfig expected = new JettyIoPoolConfig()
                .setMaxThreads(33)
                .setMinThreads(11);

        assertFullMapping(properties, expected);
    }

    @Test
    public void testLegacyProperties()
    {
        Map<String, String> currentProperties = new ImmutableMap.Builder<String, String>()
                .put("http-client.max-threads", "111")
                .build();

        Map<String, String> oldProperties = new ImmutableMap.Builder<String, String>()
                .put("http-client.threads", "111")
                .build();

        assertLegacyEquivalence(JettyIoPoolConfig.class, currentProperties, oldProperties);
    }
}
