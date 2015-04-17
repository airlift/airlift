package io.airlift.security;

import com.google.common.collect.ImmutableMap;
import io.airlift.configuration.testing.ConfigAssertions;
import io.airlift.security.client.ClientSecurityConfig;
import org.testng.annotations.Test;

import java.util.Map;

public class TestSecurityClientConfig
{
    @Test
    public void testDefaults()
    {
        ConfigAssertions.assertRecordedDefaults(ConfigAssertions.recordDefaults(ClientSecurityConfig.class)
                .setKrb5ConfPath(null)
                .setAuthenticationEnabled(false)
                .setServiceName(null)
        );
    }

    @Test
    public void testExplicitPropertyMappings()
    {
        Map<String, String> properties = new ImmutableMap.Builder<String, String>()
                .put("http.authentication.negotiate.krb5conf", "/etc/krb5.conf")
                .put("http.client.authentication.enabled", "true")
                .put("http.client.authentication.negotiate.service-name", "airlift")
                .build();

        ClientSecurityConfig expected = new ClientSecurityConfig()
                .setKrb5ConfPath("/etc/krb5.conf")
                .setAuthenticationEnabled(true)
                .setServiceName("airlift");

        ConfigAssertions.assertFullMapping(properties, expected);
    }
}
