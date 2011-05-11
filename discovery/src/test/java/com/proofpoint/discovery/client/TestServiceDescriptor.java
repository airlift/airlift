package com.proofpoint.discovery.client;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Resources;
import com.proofpoint.json.JsonCodec;
import org.testng.annotations.Test;

import java.util.UUID;

import static com.proofpoint.json.JsonCodec.jsonCodec;
import static com.proofpoint.testing.EquivalenceTester.equivalenceTester;
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
                ImmutableMap.of("a", "apple", "b", "banana"));

        String json = Resources.toString(Resources.getResource("service-descriptor.json"), Charsets.UTF_8);
        ServiceDescriptor actual = serviceDescriptorCodec.fromJson(json);

        assertEquals(actual, expected);
        assertEquals(actual.getId(), expected.getId());
        assertEquals(actual.getNodeId(), expected.getNodeId());
        assertEquals(actual.getType(), expected.getType());
        assertEquals(actual.getPool(), expected.getPool());
        assertEquals(actual.getLocation(), expected.getLocation());
        assertEquals(actual.getProperties(), expected.getProperties());
    }

    @Test
    public void testToString()
    {
        assertNotNull(new ServiceDescriptor(UUID.fromString("12345678-1234-1234-1234-123456789012"),
                "node",
                "type",
                "pool",
                "location",
                ImmutableMap.of("a", "apple", "b", "banana")));
    }

    @Test
    public void testEquivalence()
    {
        UUID serviceA = UUID.randomUUID();
        UUID serviceB = UUID.randomUUID();
        equivalenceTester()
                .addEquivalentGroup(
                        new ServiceDescriptor(serviceA, "node", "type", "pool", "location", ImmutableMap.of("a", "apple")),
                        new ServiceDescriptor(serviceA, "node-X", "type", "pool", "location", ImmutableMap.of("a", "apple")),
                        new ServiceDescriptor(serviceA, "node", "type-X", "pool", "location", ImmutableMap.of("a", "apple")),
                        new ServiceDescriptor(serviceA, "node", "type", "pool-X", "location", ImmutableMap.of("a", "apple")),
                        new ServiceDescriptor(serviceA, "node", "type", "pool", "location-X", ImmutableMap.of("a", "apple")),
                        new ServiceDescriptor(serviceA, "node", "type", "pool", "location", ImmutableMap.of("a-X", "apple")),
                        new ServiceDescriptor(serviceA, "node", "type", "pool", "location", ImmutableMap.of("a", "apple-X"))
                )
                .addEquivalentGroup(
                        new ServiceDescriptor(serviceB, "node", "type", "pool", "location", ImmutableMap.of("a", "apple")),
                        new ServiceDescriptor(serviceB, "node-X", "type", "pool", "location", ImmutableMap.of("a", "apple")),
                        new ServiceDescriptor(serviceB, "node", "type-X", "pool", "location", ImmutableMap.of("a", "apple")),
                        new ServiceDescriptor(serviceB, "node", "type", "pool-X", "location", ImmutableMap.of("a", "apple")),
                        new ServiceDescriptor(serviceB, "node", "type", "pool", "location-X", ImmutableMap.of("a", "apple")),
                        new ServiceDescriptor(serviceB, "node", "type", "pool", "location", ImmutableMap.of("a-X", "apple")),
                        new ServiceDescriptor(serviceB, "node", "type", "pool", "location", ImmutableMap.of("a", "apple-X"))
                )
                .check();
    }

}
