package com.proofpoint.node;

import com.google.common.net.InetAddresses;
import com.proofpoint.testing.Assertions;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.net.InetAddress;

public class TestNodeInfo
{
    @Test
    public void testFullConfig()
    {
        long testStartTime = System.currentTimeMillis();

        String nodeId = "nodeId";
        InetAddress publicIp = InetAddresses.forString("10.0.0.22");

        NodeInfo nodeInfo = new NodeInfo(nodeId, publicIp);
        Assert.assertNotNull(nodeInfo);
        Assert.assertEquals(nodeInfo.getNodeId(), nodeId);
        Assert.assertNotNull(nodeInfo.getInstanceId());

        Assertions.assertNotEquals(nodeInfo.getNodeId(), nodeInfo.getInstanceId());

        Assert.assertEquals(nodeInfo.getPublicIp(), publicIp);
        Assert.assertEquals(nodeInfo.getBindIp(), publicIp);
        Assertions.assertGreaterThanOrEqual(nodeInfo.getStartTime(), testStartTime);

        // make sure toString doesn't throw an exception
        Assert.assertNotNull(nodeInfo.toString());
    }
}
