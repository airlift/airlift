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

import com.google.common.collect.ImmutableMap;
import com.google.common.net.InetAddresses;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.proofpoint.configuration.ConfigurationFactory;
import com.proofpoint.configuration.ConfigurationModule;
import com.proofpoint.testing.Assertions;
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
        Assert.assertNotNull(nodeInfo.getInternalHostname());
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
        String internalHostname = "internal.hostname";
        ConfigurationFactory configFactory = new ConfigurationFactory(ImmutableMap.<String, String>builder()
                .put("node.environment", environment)
                .put("node.pool", pool)
                .put("node.id", nodeId)
                .put("node.ip", publicIp)
                .put("node.hostname", internalHostname)
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
        Assert.assertEquals(nodeInfo.getInternalHostname(), "internal.hostname");
        Assert.assertEquals(nodeInfo.getBindIp(), InetAddresses.forString("0.0.0.0"));
        Assertions.assertGreaterThanOrEqual(nodeInfo.getStartTime(), testStartTime);

        // make sure toString doesn't throw an exception
        Assert.assertNotNull(nodeInfo.toString());
    }
}
