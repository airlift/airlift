package io.airlift.node;

import com.google.common.collect.ImmutableMap;
import com.google.common.net.InetAddresses;
import com.google.inject.Guice;
import com.google.inject.Injector;
import io.airlift.configuration.ConfigurationFactory;
import io.airlift.configuration.ConfigurationModule;
import io.airlift.testing.Assertions;
import org.testng.Assert;
import org.testng.annotations.Test;

public class TestNodeModule
{
    @Test
    public void testDefaultConfig()
    {
        long testStartTime = System.currentTimeMillis();

        ConfigurationFactory configFactory = new ConfigurationFactory(ImmutableMap.<String, String>of("node.environment", "environment"));
        Injector injector = Guice.createInjector(new NodeModule(), new ConfigurationModule(configFactory));
        NodeInfo nodeInfo = injector.getInstance(NodeInfo.class);
        Assert.assertNotNull(nodeInfo);
        Assert.assertEquals(nodeInfo.getEnvironment(), "environment");
        Assert.assertEquals(nodeInfo.getPool(), "general");
        Assert.assertNotNull(nodeInfo.getNodeId());
        Assert.assertNotNull(nodeInfo.getLocation());
        Assert.assertNull(nodeInfo.getBinarySpec());
        Assert.assertNull(nodeInfo.getConfigSpec());
        Assert.assertNotNull(nodeInfo.getInstanceId());

        Assertions.assertNotEquals(nodeInfo.getNodeId(), nodeInfo.getInstanceId());

        Assert.assertNotNull(nodeInfo.getInternalIp());
        Assert.assertFalse(nodeInfo.getInternalIp().isAnyLocalAddress());
        Assert.assertNotNull(nodeInfo.getBindIp());
        Assert.assertTrue(nodeInfo.getBindIp().isAnyLocalAddress());
        Assertions.assertGreaterThanOrEqual(nodeInfo.getStartTime(), testStartTime);

        // make sure toString doesn't throw an exception
        Assert.assertNotNull(nodeInfo.toString());
    }

    @Test
    public void testFullConfig()
    {
        long testStartTime = System.currentTimeMillis();

        String environment = "environment";
        String pool = "pool";
        String nodeId = "nodeId";
        String location = "location";
        String binarySpec = "binary";
        String configSpec = "config";
        String publicIp = "10.0.0.22";
        ConfigurationFactory configFactory = new ConfigurationFactory(ImmutableMap.<String, String>builder()
                .put("node.environment", environment)
                .put("node.pool", pool)
                .put("node.id", nodeId)
                .put("node.ip", publicIp)
                .put("node.location", location)
                .put("node.binary-spec", binarySpec)
                .put("node.config-spec", configSpec)
                .build()
        );

        Injector injector = Guice.createInjector(new NodeModule(), new ConfigurationModule(configFactory));
        NodeInfo nodeInfo = injector.getInstance(NodeInfo.class);
        Assert.assertNotNull(nodeInfo);
        Assert.assertEquals(nodeInfo.getEnvironment(), environment);
        Assert.assertEquals(nodeInfo.getPool(), pool);
        Assert.assertEquals(nodeInfo.getNodeId(), nodeId);
        Assert.assertEquals(nodeInfo.getLocation(), location);
        Assert.assertEquals(nodeInfo.getBinarySpec(), binarySpec);
        Assert.assertEquals(nodeInfo.getConfigSpec(), configSpec);
        Assert.assertNotNull(nodeInfo.getInstanceId());

        Assertions.assertNotEquals(nodeInfo.getNodeId(), nodeInfo.getInstanceId());

        Assert.assertEquals(nodeInfo.getInternalIp(), InetAddresses.forString(publicIp));
        Assert.assertEquals(nodeInfo.getBindIp(), InetAddresses.forString("0.0.0.0"));
        Assertions.assertGreaterThanOrEqual(nodeInfo.getStartTime(), testStartTime);

        // make sure toString doesn't throw an exception
        Assert.assertNotNull(nodeInfo.toString());
    }
}
