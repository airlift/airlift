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
                .setNodeId(null)
                .setNodeIp((String) null));
    }

    @Test
    public void testExplicitPropertyMappings()
    {
        Map<String, String> properties = new ImmutableMap.Builder<String, String>()
                .put("node.id", "nodeId")
                .put("node.ip", "10.9.8.7")
                .build();

        NodeConfig expected = new NodeConfig()
                .setNodeId("nodeId")
                .setNodeIp(InetAddresses.forString("10.9.8.7"));

        ConfigAssertions.assertFullMapping(properties, expected);
    }

    @Test
    public void testDeprecatedProperties()
    {
        Map<String, String> currentProperties = new ImmutableMap.Builder<String, String>()
                .put("node.ip", "1.2.3.4")
                .build();

        Map<String, String> oldProperties = new ImmutableMap.Builder<String, String>()
                .put("http-server.ip", "1.2.3.4")
                .put("jetty.ip", "1.2.3.4")
                .build();

        ConfigAssertions.assertDeprecatedEquivalence(NodeConfig.class, currentProperties, oldProperties);
    }

}
