package com.proofpoint.experimental.discovery.client;

import com.google.common.collect.ImmutableMap;
import com.proofpoint.configuration.testing.ConfigAssertions;
import org.testng.annotations.Test;

import java.util.Map;

public class TestServiceSelectorConfig
{
    @Test
    public void testDefaults()
    {
        ConfigAssertions.assertRecordedDefaults(ConfigAssertions.recordDefaults(ServiceSelectorConfig.class)
                .setPool(ServiceSelectorConfig.DEFAULT_POOL));
    }

    @Test
    public void testExplicitPropertyMappings()
    {
        Map<String, String> properties = new ImmutableMap.Builder<String, String>()
                .put("pool", "test-pool")
                .build();

        ServiceSelectorConfig expected = new ServiceSelectorConfig()
                .setPool("test-pool");

        ConfigAssertions.assertFullMapping(properties, expected);
    }

}
