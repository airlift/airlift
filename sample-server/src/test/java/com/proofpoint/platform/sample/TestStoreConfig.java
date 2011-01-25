package com.proofpoint.platform.sample;

import com.google.common.collect.ImmutableMap;
import com.proofpoint.configuration.testing.ConfigAssertions;
import com.proofpoint.units.Duration;
import org.testng.annotations.Test;

import java.util.Map;
import java.util.concurrent.TimeUnit;

public class TestStoreConfig
{
    @Test
    public void testDefaults()
    {
        ConfigAssertions.assertRecordedDefaults(ConfigAssertions.recordDefaults(StoreConfig.class)
                .setTtl(new Duration(1, TimeUnit.HOURS)));
    }

    @Test
    public void testExplicitPropertyMappings()
    {
        Map<String, String> properties = new ImmutableMap.Builder<String, String>()
                .put("store.ttl", "2h")
                .build();

        StoreConfig expected = new StoreConfig()
                .setTtl(new Duration(2, TimeUnit.HOURS));

        ConfigAssertions.assertFullMapping(properties, expected);
    }

    @Test
    public void testDeprecatedProperties()
    {
        Map<String, String> currentProperties = new ImmutableMap.Builder<String, String>()
                .put("store.ttl", "1h")
                .build();

        Map<String, String> oldProperties = new ImmutableMap.Builder<String, String>()
                .put("store.ttl-in-ms", "3600000")
                .build();

        ConfigAssertions.assertDeprecatedEquivalence(StoreConfig.class, currentProperties, oldProperties);
    }
}
