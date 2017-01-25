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
package io.airlift.discovery.client;

import com.google.common.collect.ImmutableMap;
import com.google.common.io.Resources;
import io.airlift.json.JsonCodec;
import io.airlift.node.NodeConfig;
import io.airlift.node.NodeInfo;
import org.testng.annotations.Test;

import java.util.Map;
import java.util.UUID;

import static io.airlift.discovery.client.ServiceDescriptor.ServiceDescriptorBuilder;
import static io.airlift.discovery.client.ServiceDescriptor.serviceDescriptor;
import static io.airlift.json.JsonCodec.jsonCodec;
import static io.airlift.testing.EquivalenceTester.equivalenceTester;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

public class TestServiceDescriptor
{
    private final JsonCodec<ServiceDescriptor> serviceDescriptorCodec = jsonCodec(ServiceDescriptor.class);

    @Test
    public void testJsonDecode()
            throws Exception
    {
        ServiceDescriptor expected = new ServiceDescriptor(UUID.fromString("12345678-1234-1234-1234-123456789012"),
                "node",
                "type",
                "pool",
                "location",
                ServiceState.RUNNING, ImmutableMap.of("a", "apple", "b", "banana"));

        String json = Resources.toString(Resources.getResource("service-descriptor.json"), UTF_8);
        ServiceDescriptor actual = serviceDescriptorCodec.fromJson(json);

        assertDescriptorEquals(expected, actual);
    }

    @Test
    public void testToString()
    {
        assertNotNull(new ServiceDescriptor(UUID.fromString("12345678-1234-1234-1234-123456789012"),
                "node",
                "type",
                "pool",
                "location",
                ServiceState.RUNNING, ImmutableMap.of("a", "apple", "b", "banana")));
    }

    @Test
    public void testEquivalence()
    {
        UUID serviceA = UUID.randomUUID();
        UUID serviceB = UUID.randomUUID();
        equivalenceTester()
                .addEquivalentGroup(
                        new ServiceDescriptor(serviceA, "node", "type", "pool", "location", ServiceState.RUNNING, ImmutableMap.of("a", "apple")),
                        new ServiceDescriptor(serviceA, "node-X", "type", "pool", "location", ServiceState.RUNNING, ImmutableMap.of("a", "apple")),
                        new ServiceDescriptor(serviceA, "node", "type-X", "pool", "location", ServiceState.RUNNING, ImmutableMap.of("a", "apple")),
                        new ServiceDescriptor(serviceA, "node", "type", "pool-X", "location", ServiceState.RUNNING, ImmutableMap.of("a", "apple")),
                        new ServiceDescriptor(serviceA, "node", "type", "pool", "location-X", ServiceState.RUNNING, ImmutableMap.of("a", "apple")),
                        new ServiceDescriptor(serviceA, "node", "type", "pool", "location", ServiceState.RUNNING, ImmutableMap.of("a-X", "apple")),
                        new ServiceDescriptor(serviceA, "node", "type", "pool", "location", ServiceState.RUNNING, ImmutableMap.of("a", "apple-X"))
                )
                .addEquivalentGroup(
                        new ServiceDescriptor(serviceB, "node", "type", "pool", "location", ServiceState.RUNNING, ImmutableMap.of("a", "apple")),
                        new ServiceDescriptor(serviceB, "node-X", "type", "pool", "location", ServiceState.RUNNING, ImmutableMap.of("a", "apple")),
                        new ServiceDescriptor(serviceB, "node", "type-X", "pool", "location", ServiceState.RUNNING, ImmutableMap.of("a", "apple")),
                        new ServiceDescriptor(serviceB, "node", "type", "pool-X", "location", ServiceState.RUNNING, ImmutableMap.of("a", "apple")),
                        new ServiceDescriptor(serviceB, "node", "type", "pool", "location-X", ServiceState.RUNNING, ImmutableMap.of("a", "apple")),
                        new ServiceDescriptor(serviceB, "node", "type", "pool", "location", ServiceState.RUNNING, ImmutableMap.of("a-X", "apple")),
                        new ServiceDescriptor(serviceB, "node", "type", "pool", "location", ServiceState.RUNNING, ImmutableMap.of("a", "apple-X"))
                )
                .check();
    }

    @Test
    public void testBuilderNodeId()
    {
        ServiceDescriptor expected = new ServiceDescriptor(
                UUID.fromString("12345678-1234-1234-1234-123456789012"),
                "node",
                "type",
                "pool",
                "location",
                ServiceState.RUNNING,
                ImmutableMap.of("a", "apple", "b", "banana"));

        ServiceDescriptorBuilder builder = serviceDescriptor(expected.getType())
                .setId(expected.getId())
                .setLocation(expected.getLocation())
                .setNodeId(expected.getNodeId())
                .setPool(expected.getPool())
                .setState(expected.getState());

        for (Map.Entry<String, String> entry : expected.getProperties().entrySet()) {
            builder.addProperty(entry.getKey(), entry.getValue());
        }

        assertDescriptorEquals(expected, builder.build());
    }

    @Test
    public void testBuilderNodeInfo()
    {
        NodeInfo nodeInfo = new NodeInfo(new NodeConfig().setEnvironment("test").setPool("pool"));

        ServiceDescriptor expected = new ServiceDescriptor(
                UUID.fromString("12345678-1234-1234-1234-123456789012"),
                nodeInfo.getNodeId(),
                "type",
                nodeInfo.getPool(),
                "location",
                ServiceState.STOPPED,
                ImmutableMap.of("a", "apple", "b", "banana"));

        ServiceDescriptor actual = serviceDescriptor(expected.getType())
                .setId(expected.getId())
                .setLocation(expected.getLocation())
                .setNodeInfo(nodeInfo)
                .setState(expected.getState())
                .addProperties(expected.getProperties())
                .build();

        assertDescriptorEquals(expected, actual);
    }

    private static void assertDescriptorEquals(ServiceDescriptor expected, ServiceDescriptor actual)
    {
        assertEquals(actual, expected);
        assertEquals(actual.getId(), expected.getId());
        assertEquals(actual.getNodeId(), expected.getNodeId());
        assertEquals(actual.getType(), expected.getType());
        assertEquals(actual.getPool(), expected.getPool());
        assertEquals(actual.getLocation(), expected.getLocation());
        assertEquals(actual.getProperties(), expected.getProperties());
    }
}
