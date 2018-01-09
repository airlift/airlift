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

import com.google.common.collect.ImmutableMap;
import com.google.common.net.InetAddresses;
import com.google.inject.Guice;
import com.google.inject.Injector;
import io.airlift.configuration.ConfigurationFactory;
import io.airlift.configuration.ConfigurationModule;
import org.testng.annotations.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;

import static io.airlift.testing.Assertions.assertGreaterThanOrEqual;
import static io.airlift.testing.Assertions.assertNotEquals;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

public class TestNodeModule
{
    @Test
    public void testDefaultConfig()
            throws UnknownHostException
    {
        long testStartTime = System.currentTimeMillis();

        ConfigurationFactory configFactory = new ConfigurationFactory(ImmutableMap.<String, String>of("node.environment", "environment"));
        Injector injector = Guice.createInjector(new NodeModule(), new ConfigurationModule(configFactory));
        NodeInfo nodeInfo = injector.getInstance(NodeInfo.class);
        assertNotNull(nodeInfo);
        assertEquals(nodeInfo.getEnvironment(), "environment");
        assertEquals(nodeInfo.getPool(), "general");
        assertNotNull(nodeInfo.getNodeId());
        assertNotNull(nodeInfo.getLocation());
        assertNull(nodeInfo.getBinarySpec());
        assertNull(nodeInfo.getConfigSpec());
        assertNotNull(nodeInfo.getInstanceId());

        assertNotEquals(nodeInfo.getNodeId(), nodeInfo.getInstanceId());

        assertNotNull(nodeInfo.getInternalAddress());
        assertFalse(InetAddress.getByName(nodeInfo.getInternalAddress()).isAnyLocalAddress());
        assertNotNull(nodeInfo.getBindIp());
        assertTrue(nodeInfo.getBindIp().isAnyLocalAddress());
        assertGreaterThanOrEqual(nodeInfo.getStartTime(), testStartTime);

        // make sure toString doesn't throw an exception
        assertNotNull(nodeInfo.toString());
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
        String publicAddress = "public";
        ConfigurationFactory configFactory = new ConfigurationFactory(ImmutableMap.<String, String>builder()
                .put("node.environment", environment)
                .put("node.pool", pool)
                .put("node.id", nodeId)
                .put("node.internal-address", publicAddress)
                .put("node.location", location)
                .put("node.binary-spec", binarySpec)
                .put("node.config-spec", configSpec)
                .build());

        Injector injector = Guice.createInjector(new NodeModule(), new ConfigurationModule(configFactory));
        NodeInfo nodeInfo = injector.getInstance(NodeInfo.class);
        assertNotNull(nodeInfo);
        assertEquals(nodeInfo.getEnvironment(), environment);
        assertEquals(nodeInfo.getPool(), pool);
        assertEquals(nodeInfo.getNodeId(), nodeId);
        assertEquals(nodeInfo.getLocation(), location);
        assertEquals(nodeInfo.getBinarySpec(), binarySpec);
        assertEquals(nodeInfo.getConfigSpec(), configSpec);
        assertNotNull(nodeInfo.getInstanceId());

        assertNotEquals(nodeInfo.getNodeId(), nodeInfo.getInstanceId());

        assertEquals(nodeInfo.getInternalAddress(), publicAddress);
        assertEquals(nodeInfo.getBindIp(), InetAddresses.forString("0.0.0.0"));
        assertGreaterThanOrEqual(nodeInfo.getStartTime(), testStartTime);

        // make sure toString doesn't throw an exception
        assertNotNull(nodeInfo.toString());
    }
}
