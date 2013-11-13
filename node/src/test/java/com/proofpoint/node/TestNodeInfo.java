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
package com.proofpoint.node;

import com.google.common.net.InetAddresses;
import com.proofpoint.testing.Assertions;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.net.InetAddress;

public class TestNodeInfo
{
    public static final String APPLICATION = "application_1234";
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
        InetAddress internalIp = InetAddresses.forString("10.0.0.22");
        String internalHostname = "internal.hostname";
        InetAddress bindIp = InetAddresses.forString("10.0.0.33");
        String externalAddress = "external";

        NodeInfo nodeInfo = new NodeInfo(APPLICATION, ENVIRONMENT, POOL, nodeId, internalIp, internalHostname, bindIp, externalAddress, location, binarySpec, configSpec);
        Assert.assertEquals(nodeInfo.getApplication(), APPLICATION);
        Assert.assertEquals(nodeInfo.getEnvironment(), ENVIRONMENT);
        Assert.assertEquals(nodeInfo.getPool(), POOL);
        Assert.assertEquals(nodeInfo.getNodeId(), nodeId);
        Assert.assertEquals(nodeInfo.getLocation(), location);
        Assert.assertEquals(nodeInfo.getBinarySpec(), binarySpec);
        Assert.assertEquals(nodeInfo.getConfigSpec(), configSpec);
        Assert.assertNotNull(nodeInfo.getInstanceId());

        Assertions.assertNotEquals(nodeInfo.getNodeId(), nodeInfo.getInstanceId());

        Assert.assertEquals(nodeInfo.getInternalIp(), internalIp);
        Assert.assertEquals(nodeInfo.getExternalAddress(), externalAddress);
        Assert.assertEquals(nodeInfo.getBindIp(), bindIp);
        Assertions.assertGreaterThanOrEqual(nodeInfo.getStartTime(), testStartTime);

        // make sure toString doesn't throw an exception
        Assert.assertNotNull(nodeInfo.toString());
    }

    @Test
    public void testDefaultAddresses()
    {
        NodeInfo nodeInfo = new NodeInfo(APPLICATION, ENVIRONMENT, POOL, "nodeInfo", InetAddresses.forString("10.0.0.22"), null, null, null, null, null, null);
        Assert.assertEquals(nodeInfo.getExternalAddress(), "10.0.0.22");
        Assert.assertEquals(nodeInfo.getBindIp(), InetAddresses.forString("0.0.0.0"));
    }

    @Test(expectedExceptions = IllegalArgumentException.class, expectedExceptionsMessageRegExp = "nodeId .*")
    public void testInvalidNodeId()
    {
        new NodeInfo(APPLICATION, ENVIRONMENT, POOL, "abc/123", null, null, null, null, null, null, null);
    }

    @Test(expectedExceptions = IllegalArgumentException.class, expectedExceptionsMessageRegExp = "environment .*")
    public void testInvalidEnvironment()
    {
        new NodeInfo(APPLICATION, "ENV", POOL, null, null, null, null, null, null, null, null);
    }

    @Test(expectedExceptions = IllegalArgumentException.class, expectedExceptionsMessageRegExp = "hostname .*")
    public void testInvalidHostname()
    {
        new NodeInfo(APPLICATION, ENVIRONMENT, POOL, null, null, "unqualified", null, null, null, null, null);
    }

    @Test(expectedExceptions = IllegalArgumentException.class, expectedExceptionsMessageRegExp = "pool .*")
    public void testInvalidPool()
    {
        new NodeInfo(APPLICATION, ENVIRONMENT, "P@OOL", null, null, null, null, null, null, null, null);
    }
}
