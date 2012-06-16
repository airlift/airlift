package com.proofpoint.http.client;

import com.google.common.collect.ImmutableMap;
import com.proofpoint.configuration.testing.ConfigAssertions;
import com.proofpoint.units.Duration;
import org.testng.annotations.Test;

import javax.validation.constraints.NotNull;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.proofpoint.testing.ValidationAssertions.assertFailsValidation;

public class TestHttpClientConfig
{
    @Test
    public void testDefaults()
    {
        ConfigAssertions.assertRecordedDefaults(ConfigAssertions.recordDefaults(HttpClientConfig.class)
                .setConnectTimeout(new Duration(1, TimeUnit.SECONDS))
                .setReadTimeout(new Duration(1, TimeUnit.MINUTES))
                .setMaxConnections(200)
                .setMaxConnectionsPerServer(20));
    }

    @Test
    public void testExplicitPropertyMappings()
    {
        Map<String, String> properties = new ImmutableMap.Builder<String, String>()
                .put("http-client.connect-timeout", "4s")
                .put("http-client.read-timeout", "5s")
                .put("http-client.max-connections", "12")
                .put("http-client.max-connections-per-server", "3")
                .build();

        HttpClientConfig expected = new HttpClientConfig()
                .setConnectTimeout(new Duration(4, TimeUnit.SECONDS))
                .setReadTimeout(new Duration(5, TimeUnit.SECONDS))
                .setMaxConnections(12)
                .setMaxConnectionsPerServer(3);

        ConfigAssertions.assertFullMapping(properties, expected);
    }

    @Test
    public void testValidations()
    {
        assertFailsValidation(new HttpClientConfig().setConnectTimeout(null), "connectTimeout", "may not be null", NotNull.class);
        assertFailsValidation(new HttpClientConfig().setReadTimeout(null), "readTimeout", "may not be null", NotNull.class);
    }
}
