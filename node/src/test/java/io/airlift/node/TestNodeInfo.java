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
import org.testng.annotations.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;

import static io.airlift.node.NodeConfig.AddressSource.FQDN;
import static io.airlift.node.NodeConfig.AddressSource.HOSTNAME;
import static io.airlift.node.NodeConfig.AddressSource.IP;
import static io.airlift.testing.Assertions.assertGreaterThanOrEqual;
import static io.airlift.testing.Assertions.assertNotEquals;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

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
        assertEquals(nodeInfo.getEnvironment(), ENVIRONMENT);
        assertEquals(nodeInfo.getPool(), POOL);
        assertEquals(nodeInfo.getNodeId(), nodeId);
        assertEquals(nodeInfo.getLocation(), location);
        assertEquals(nodeInfo.getBinarySpec(), binarySpec);
        assertEquals(nodeInfo.getConfigSpec(), configSpec);
        assertNotNull(nodeInfo.getInstanceId());

        assertNotEquals(nodeInfo.getNodeId(), nodeInfo.getInstanceId());

        assertEquals(nodeInfo.getInternalAddress(), internalIp);
        assertEquals(nodeInfo.getExternalAddress(), externalAddress);
        assertEquals(nodeInfo.getBindIp(), bindIp);
        assertGreaterThanOrEqual(nodeInfo.getStartTime(), testStartTime);

        // make sure toString doesn't throw an exception
        assertNotNull(nodeInfo.toString());
    }

    @Test
    public void testDefaultAddresses()
    {
        NodeInfo nodeInfo = new NodeInfo(ENVIRONMENT, POOL, "nodeInfo", "10.0.0.22", null, null, null, null, null, IP);
        assertEquals(nodeInfo.getExternalAddress(), "10.0.0.22");
        assertEquals(nodeInfo.getBindIp(), InetAddresses.forString("0.0.0.0"));
    }

    @Test
    public void testIpDiscovery()
            throws UnknownHostException
    {
        NodeInfo nodeInfo = new NodeInfo(ENVIRONMENT, POOL, "nodeInfo", null, null, null, null, null, null, IP);
        assertNotNull(nodeInfo.getInternalAddress());
        assertEquals(nodeInfo.getBindIp(), InetAddresses.forString("0.0.0.0"));
        assertEquals(nodeInfo.getExternalAddress(), nodeInfo.getInternalAddress());
    }

    @Test
    public void testHostnameDiscovery()
            throws UnknownHostException
    {
        NodeInfo nodeInfo = new NodeInfo(ENVIRONMENT, POOL, "nodeInfo", null, null, null, null, null, null, HOSTNAME);
        assertNotNull(nodeInfo.getInternalAddress());
        assertEquals(nodeInfo.getBindIp(), InetAddresses.forString("0.0.0.0"));
        assertEquals(nodeInfo.getExternalAddress(), InetAddress.getLocalHost().getHostName());
    }

    @Test
    public void testFqdnDiscovery()
            throws UnknownHostException
    {
        NodeInfo nodeInfo = new NodeInfo(ENVIRONMENT, POOL, "nodeInfo", null, null, null, null, null, null, FQDN);
        assertNotNull(nodeInfo.getInternalAddress());
        assertEquals(nodeInfo.getBindIp(), InetAddresses.forString("0.0.0.0"));
        assertEquals(nodeInfo.getExternalAddress(), InetAddress.getLocalHost().getCanonicalHostName());
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
