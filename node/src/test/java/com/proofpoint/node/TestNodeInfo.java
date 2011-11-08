package com.proofpoint.node;

import com.google.common.net.InetAddresses;
import com.proofpoint.testing.Assertions;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.net.InetAddress;

public class TestNodeInfo
{
    public static final String ENVIRONMENT = "environment_1234";
    public static final String POOL = "pool_1234";

    @Test
    public void testBasicNodeInfo()
    {
        long testStartTime = System.currentTimeMillis();

        String nodeId = "nodeId";
        String location = "location";
        String binarySpec = "binary";
        String configSpec = "config";
        InetAddress publicIp = InetAddresses.forString("10.0.0.22");

        NodeInfo nodeInfo = new NodeInfo(ENVIRONMENT, POOL, nodeId, publicIp, location, binarySpec, configSpec);
        Assert.assertEquals(nodeInfo.getEnvironment(), ENVIRONMENT);
        Assert.assertEquals(nodeInfo.getPool(), POOL);
        Assert.assertEquals(nodeInfo.getNodeId(), nodeId);
        Assert.assertEquals(nodeInfo.getLocation(), location);
        Assert.assertEquals(nodeInfo.getBinarySpec(), binarySpec);
        Assert.assertEquals(nodeInfo.getConfigSpec(), configSpec);
        Assert.assertNotNull(nodeInfo.getInstanceId());

        Assertions.assertNotEquals(nodeInfo.getNodeId(), nodeInfo.getInstanceId());

        Assert.assertEquals(nodeInfo.getPublicIp(), publicIp);
        Assert.assertEquals(nodeInfo.getBindIp(), publicIp);
        Assertions.assertGreaterThanOrEqual(nodeInfo.getStartTime(), testStartTime);

        // make sure toString doesn't throw an exception
        Assert.assertNotNull(nodeInfo.toString());
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testInvalidEnvironment()
    {
        new NodeInfo("ENV", POOL, null, null, null, null, null);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testInvalidPool()
    {
        new NodeInfo(ENVIRONMENT, "POOL", null, null, null, null, null);
    }
}
