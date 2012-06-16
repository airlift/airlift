package io.airlift.http.client;

import com.google.common.collect.ImmutableMap;
import io.airlift.configuration.testing.ConfigAssertions;
import org.testng.annotations.Test;

import java.util.Map;

public class TestAsyncHttpClientConfig
{
    @Test
    public void testDefaults()
    {
        ConfigAssertions.assertRecordedDefaults(ConfigAssertions.recordDefaults(AsyncHttpClientConfig.class)
                .setWorkerThreads(16));
    }

    @Test
    public void testExplicitPropertyMappings()
    {
        Map<String, String> properties = new ImmutableMap.Builder<String, String>()
                .put("http-client.threads", "33")
                .build();

        AsyncHttpClientConfig expected = new AsyncHttpClientConfig()
                .setWorkerThreads(33);

        ConfigAssertions.assertFullMapping(properties, expected);
    }
}
