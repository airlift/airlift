package io.airlift.http.client.jetty;

import com.google.common.collect.ImmutableMap;
import io.airlift.configuration.testing.ConfigAssertions;
import org.testng.annotations.Test;

import java.util.Map;

public class TestJettyIoPoolConfig
{
    @Test
    public void testDefaults()
    {
        ConfigAssertions.assertRecordedDefaults(ConfigAssertions.recordDefaults(JettyIoPoolConfig.class)
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

        ConfigAssertions.assertFullMapping(properties, expected);
    }
}
