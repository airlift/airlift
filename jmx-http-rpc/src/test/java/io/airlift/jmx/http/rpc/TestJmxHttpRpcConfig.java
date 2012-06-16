package io.airlift.jmx.http.rpc;

import com.google.common.collect.ImmutableMap;
import io.airlift.configuration.testing.ConfigAssertions;
import org.testng.annotations.Test;

import java.util.Map;

public class TestJmxHttpRpcConfig
{
    @Test
    public void testDefaults()
    {
        ConfigAssertions.assertRecordedDefaults(ConfigAssertions.recordDefaults(JmxHttpRpcConfig.class)
                .setUsername(null)
                .setPassword(null)
        );
    }

    @Test
    public void testExplicitPropertyMappings()
    {
        Map<String, String> properties = new ImmutableMap.Builder<String, String>()
                .put("jmx-http-rpc.username", "user")
                .put("jmx-http-rpc.password", "pass")
                .build();

        JmxHttpRpcConfig expected = new JmxHttpRpcConfig()
                .setUsername("user")
                .setPassword("pass");

        ConfigAssertions.assertFullMapping(properties, expected);
    }
}
