package com.proofpoint.discovery.client.http;

import com.google.common.collect.ImmutableMap;
import com.proofpoint.configuration.testing.ConfigAssertions;
import org.testng.annotations.Test;

import java.util.Map;

public class TestBalancingHttpClientConfig
{
    @Test
    public void testDefaults()
    {
        ConfigAssertions.assertRecordedDefaults(ConfigAssertions.recordDefaults(BalancingHttpClientConfig.class)
                .setMaxRetries(2));
    }

    @Test
    public void testExplicitPropertyMappings()
    {
        Map<String, String> properties = new ImmutableMap.Builder<String, String>()
                .put("http-client.max-retries", "3")
                .build();

        BalancingHttpClientConfig expected = new BalancingHttpClientConfig()
                .setMaxRetries(3);

        ConfigAssertions.assertFullMapping(properties, expected);
    }

    @Test
    public void testDeprecatedProperties()
    {
        ConfigAssertions.assertDeprecatedEquivalence(BalancingHttpClientConfig.class,
                ImmutableMap.<String, String>of());
    }
}
