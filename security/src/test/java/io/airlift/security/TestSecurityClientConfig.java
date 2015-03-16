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
                .setAuthScheme(null)
                .setKrb5Conf(null)
                .setServiceName(null)
        );
    }

    @Test
    public void testExplicitPropertyMappings()
    {
        Map<String, String> properties = new ImmutableMap.Builder<String, String>()
                .put("http-security.client.authentication.scheme", "negotiate")
                .put("http-security.client.authentication.negotiate.krb5conf", "/etc/krb5.conf")
                .put("http-security.client.authentication.negotiate.service-name", "airlift")
                .build();

        ClientSecurityConfig expected = new ClientSecurityConfig()
                .setAuthScheme(AuthScheme.NEGOTIATE)
                .setKrb5Conf("/etc/krb5.conf")
                .setServiceName("airlift");

        ConfigAssertions.assertFullMapping(properties, expected);
    }
}
