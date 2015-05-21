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
                .setAuthenticationEnabled(false)
                .setKrb5Config(null)
                .setKrb5Keytab(null)
                .setKrb5CredentialCache(null)
                .setKrb5Principal(null)
                .setKrb5RemoteServiceName(null)
        );
    }

    @Test
    public void testExplicitPropertyMappings()
    {
        Map<String, String> properties = new ImmutableMap.Builder<String, String>()
                .put("http.client.authentication.enabled", "true")
                .put("http.authentication.krb5.config", "/etc/krb5.conf")
                .put("http.authentication.krb5.keytab", "/etc/krb5.keytab")
                .put("http.authentication.krb5.credential-cache", "/etc/krb5.ccache")
                .put("http.client.authentication.krb5.principal", "airlift-client")
                .put("http.client.authentication.krb5.remote-service-name", "airlift")
                .build();

        ClientSecurityConfig expected = new ClientSecurityConfig()
                .setAuthenticationEnabled(true)
                .setKrb5Config("/etc/krb5.conf")
                .setKrb5Keytab("/etc/krb5.keytab")
                .setKrb5CredentialCache("/etc/krb5.ccache")
                .setKrb5Principal("airlift-client")
                .setKrb5RemoteServiceName("airlift");

        ConfigAssertions.assertFullMapping(properties, expected);
    }
}
