package io.airlift.discovery.client;

import com.google.common.collect.ImmutableMap;
import io.airlift.configuration.testing.ConfigAssertions;
import org.testng.annotations.Test;

import java.net.URI;
import java.util.Map;

public class TestDiscoveryClientConfig
{
    @Test
    public void testDefaults()
    {
        ConfigAssertions.assertRecordedDefaults(ConfigAssertions.recordDefaults(DiscoveryClientConfig.class)
                .setDiscoveryServiceURI(null));
    }

    @Test
    public void testExplicitPropertyMappings()
    {
        Map<String, String> properties = new ImmutableMap.Builder<String, String>()
                .put("discovery.uri", "fake://server")
                .build();

        DiscoveryClientConfig expected = new DiscoveryClientConfig()
                .setDiscoveryServiceURI(URI.create("fake://server"));

        ConfigAssertions.assertFullMapping(properties, expected);
    }
}
