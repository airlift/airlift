package com.proofpoint.discovery.client;

import com.google.common.collect.ImmutableMap;
import com.proofpoint.configuration.testing.ConfigAssertions;
import org.testng.annotations.Test;

import java.net.URI;
import java.util.Map;

public class TestServiceInventoryConfig
{
    @Test
    public void testDefaults()
    {
        ConfigAssertions.assertRecordedDefaults(ConfigAssertions.recordDefaults(ServiceInventoryConfig.class)
                .setServiceInventoryUri(null));
    }

    @Test
    public void testExplicitPropertyMappings()
    {
        Map<String, String> properties = new ImmutableMap.Builder<String, String>()
                .put("service-inventory.uri", "fake://server")
                .build();

        ServiceInventoryConfig expected = new ServiceInventoryConfig()
                .setServiceInventoryUri(URI.create("fake://server"));

        ConfigAssertions.assertFullMapping(properties, expected);
    }
}
