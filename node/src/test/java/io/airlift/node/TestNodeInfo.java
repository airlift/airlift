/*
 * Copyright 2010 Proofpoint, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.airlift.node;

import com.google.common.net.InetAddresses;
import io.airlift.testing.Assertions;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;

import static io.airlift.node.NodeConfig.AddressSource.FQDN;
import static io.airlift.node.NodeConfig.AddressSource.HOSTNAME;
import static io.airlift.node.NodeConfig.AddressSource.IP;

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
        String internalIp = "10.0.0.22";
        InetAddress bindIp = InetAddresses.forString("10.0.0.33");
        String externalAddress = "external";

        NodeInfo nodeInfo = new NodeInfo(ENVIRONMENT, POOL, nodeId, internalIp, bindIp, externalAddress, location, binarySpec, configSpec, IP);
        Assert.assertEquals(nodeInfo.getEnvironment(), ENVIRONMENT);
        Assert.assertEquals(nodeInfo.getPool(), POOL);
        Assert.assertEquals(nodeInfo.getNodeId(), nodeId);
        Assert.assertEquals(nodeInfo.getLocation(), location);
        Assert.assertEquals(nodeInfo.getBinarySpec(), binarySpec);
        Assert.assertEquals(nodeInfo.getConfigSpec(), configSpec);
        Assert.assertNotNull(nodeInfo.getInstanceId());

        Assertions.assertNotEquals(nodeInfo.getNodeId(), nodeInfo.getInstanceId());

        Assert.assertEquals(nodeInfo.getInternalAddress(), internalIp);
        Assert.assertEquals(nodeInfo.getExternalAddress(), externalAddress);
        Assert.assertEquals(nodeInfo.getBindIp(), bindIp);
        Assertions.assertGreaterThanOrEqual(nodeInfo.getStartTime(), testStartTime);

        // make sure toString doesn't throw an exception
        Assert.assertNotNull(nodeInfo.toString());
    }

    @Test
    public void testDefaultAddresses()
    {
        NodeInfo nodeInfo = new NodeInfo(ENVIRONMENT, POOL, "nodeInfo", "10.0.0.22", null, null, null, null, null, IP);
        Assert.assertEquals(nodeInfo.getExternalAddress(), "10.0.0.22");
        Assert.assertEquals(nodeInfo.getBindIp(), InetAddresses.forString("0.0.0.0"));
    }

    @Test
    public void testIpDiscovery()
            throws UnknownHostException
    {
        NodeInfo nodeInfo = new NodeInfo(ENVIRONMENT, POOL, "nodeInfo", null, null, null, null, null, null, IP);
        Assert.assertNotNull(nodeInfo.getInternalAddress());
        Assert.assertEquals(nodeInfo.getBindIp(), InetAddresses.forString("0.0.0.0"));
        Assert.assertEquals(nodeInfo.getExternalAddress(), nodeInfo.getInternalAddress());
    }

    @Test
    public void testHostnameDiscovery()
            throws UnknownHostException
    {
        NodeInfo nodeInfo = new NodeInfo(ENVIRONMENT, POOL, "nodeInfo", null, null, null, null, null, null, HOSTNAME);
        Assert.assertNotNull(nodeInfo.getInternalAddress());
        Assert.assertEquals(nodeInfo.getBindIp(), InetAddresses.forString("0.0.0.0"));
        Assert.assertEquals(nodeInfo.getExternalAddress(), InetAddress.getLocalHost().getHostName());
    }

    @Test
    public void testFqdnDiscovery()
            throws UnknownHostException
    {
        NodeInfo nodeInfo = new NodeInfo(ENVIRONMENT, POOL, "nodeInfo", null, null, null, null, null, null, FQDN);
        Assert.assertNotNull(nodeInfo.getInternalAddress());
        Assert.assertEquals(nodeInfo.getBindIp(), InetAddresses.forString("0.0.0.0"));
        Assert.assertEquals(nodeInfo.getExternalAddress(), InetAddress.getLocalHost().getCanonicalHostName());
    }

    @Test(expectedExceptions = IllegalArgumentException.class, expectedExceptionsMessageRegExp = "nodeId .*")
    public void testInvalidNodeId()
    {
        new NodeInfo(ENVIRONMENT, POOL, "abc/123", null, null, null, null, null, null, IP);
    }

    @Test(expectedExceptions = IllegalArgumentException.class, expectedExceptionsMessageRegExp = "environment .*")
    public void testInvalidEnvironment()
    {
        new NodeInfo("ENV", POOL, null, null, null, null, null, null, null, IP);
    }

    @Test(expectedExceptions = IllegalArgumentException.class, expectedExceptionsMessageRegExp = "pool .*")
    public void testInvalidPool()
    {
        new NodeInfo(ENVIRONMENT, "POOL", null, null, null, null, null, null, null, IP);
    }
}
