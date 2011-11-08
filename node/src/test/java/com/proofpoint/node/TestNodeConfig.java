package com.proofpoint.node;

import com.google.common.collect.ImmutableMap;
import com.google.common.net.InetAddresses;
import com.proofpoint.configuration.testing.ConfigAssertions;
import org.testng.annotations.Test;

import java.util.Map;

public class TestNodeConfig
{
    @Test
    public void testDefaults()
    {
        ConfigAssertions.assertRecordedDefaults(ConfigAssertions.recordDefaults(NodeConfig.class)
                .setEnvironment(null)
                .setPool("general")
                .setNodeId(null)
                .setNodeIp((String) null)
                .setLocation(null)
                .setBinarySpec(null)
                .setConfigSpec(null));
    }

    @Test
    public void testExplicitPropertyMappings()
    {
        Map<String, String> properties = new ImmutableMap.Builder<String, String>()
                .put("node.environment", "environment")
                .put("node.pool", "pool")
                .put("node.id", "nodeId")
                .put("node.ip", "10.9.8.7")
                .put("node.location", "location")
                .put("node.binary-spec", "binary")
                .put("node.config-spec", "config")
                .build();

        NodeConfig expected = new NodeConfig()
                .setEnvironment("environment")
                .setPool("pool")
                .setNodeId("nodeId")
                .setNodeIp(InetAddresses.forString("10.9.8.7"))
                .setLocation("location")
                .setBinarySpec("binary")
                .setConfigSpec("config");

        ConfigAssertions.assertFullMapping(properties, expected);
    }

    @Test
    public void testDeprecatedProperties()
    {
        Map<String, String> currentProperties = new ImmutableMap.Builder<String, String>()
                .put("node.environment", "environment")
                .put("node.ip", "1.2.3.4")
                .build();

        Map<String, String> oldProperties = new ImmutableMap.Builder<String, String>()
                .put("node.environment", "environment")
                .put("http-server.ip", "1.2.3.4")
                .put("jetty.ip", "1.2.3.4")
                .build();

        ConfigAssertions.assertDeprecatedEquivalence(NodeConfig.class, currentProperties, oldProperties);
    }

}
