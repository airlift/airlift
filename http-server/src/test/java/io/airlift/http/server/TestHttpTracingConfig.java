package io.airlift.http.server;

import com.google.common.collect.ImmutableMap;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static io.airlift.configuration.testing.ConfigAssertions.assertFullMapping;
import static io.airlift.configuration.testing.ConfigAssertions.assertRecordedDefaults;
import static io.airlift.configuration.testing.ConfigAssertions.recordDefaults;

class TestHttpTracingConfig
{
    @Test
    public void testDefaults()
    {
        assertRecordedDefaults(recordDefaults(HttpTracingConfig.class)
                .setTracingRequestHeaders(Set.of())
                .setTracingResponseHeaders(Set.of()));
    }

    @Test
    public void testExplicitPropertyMappings()
    {
        Map<String, String> properties = new ImmutableMap.Builder<String, String>()
                .put("http-server.tracing.request-headers", "cf-ray,x-request-id")
                .put("http-server.tracing.response-headers", "x-session-id")
                .build();

        HttpTracingConfig expected = new HttpTracingConfig()
                .setTracingRequestHeaders(Set.of("cf-ray", "x-request-id"))
                .setTracingResponseHeaders(Set.of("x-session-id"));

        assertFullMapping(properties, expected);
    }
}
