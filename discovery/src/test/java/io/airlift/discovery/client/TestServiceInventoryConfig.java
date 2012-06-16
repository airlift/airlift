package com.proofpoint.discovery.client;

import com.google.common.collect.ImmutableMap;
import com.proofpoint.configuration.testing.ConfigAssertions;
import com.proofpoint.units.Duration;
import org.testng.annotations.Test;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class TestServiceInventoryConfig
{
    @Test
    public void testDefaults()
    {
        ConfigAssertions.assertRecordedDefaults(ConfigAssertions.recordDefaults(ServiceInventoryConfig.class)
                .setServiceInventoryUri(null)
                .setUpdateInterval(new Duration(10, TimeUnit.SECONDS)));
    }

    @Test
    public void testExplicitPropertyMappings()
    {
        Map<String, String> properties = new ImmutableMap.Builder<String, String>()
                .put("service-inventory.uri", "fake://server")
                .put("service-inventory.update-interval", "15m")
                .build();

        ServiceInventoryConfig expected = new ServiceInventoryConfig()
                .setServiceInventoryUri(URI.create("fake://server"))
                .setUpdateInterval(new Duration(15, TimeUnit.MINUTES));

        ConfigAssertions.assertFullMapping(properties, expected);
    }
}
