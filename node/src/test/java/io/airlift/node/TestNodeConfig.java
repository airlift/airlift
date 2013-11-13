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
import io.airlift.configuration.testing.ConfigAssertions;
import org.testng.annotations.Test;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;

import java.util.Map;
import java.util.UUID;

import static io.airlift.testing.ValidationAssertions.assertFailsValidation;
import static io.airlift.testing.ValidationAssertions.assertValidates;

public class TestNodeConfig
{
    @Test
    public void testDefaults()
    {
        ConfigAssertions.assertRecordedDefaults(ConfigAssertions.recordDefaults(NodeConfig.class)
                .setEnvironment(null)
                .setPool("general")
                .setNodeId(null)
                .setNodeInternalIp((String) null)
                .setNodeBindIp((String) null)
                .setNodeExternalAddress(null)
                .setLocation(null)
                .setBinarySpec(null)
                .setConfigSpec(null));
    }

    @Test
    public void testExplicitPropertyMappings()
    {
        Map<String, String> properties = new ImmutableMap.Builder<String, String>()
                .put("node.environment", "environment")
                .put("node.pool", "pool")
                .put("node.id", "nodeId")
                .put("node.ip", "10.9.8.7")
                .put("node.bind-ip", "10.11.12.13")
                .put("node.external-address", "external")
                .put("node.location", "location")
                .put("node.binary-spec", "binary")
                .put("node.config-spec", "config")
                .build();

        NodeConfig expected = new NodeConfig()
                .setEnvironment("environment")
                .setPool("pool")
                .setNodeId("nodeId")
                .setNodeInternalIp(InetAddresses.forString("10.9.8.7"))
                .setNodeBindIp(InetAddresses.forString("10.11.12.13"))
                .setNodeExternalAddress("external")
                .setLocation("location")
                .setBinarySpec("binary")
                .setConfigSpec("config");

        ConfigAssertions.assertFullMapping(properties, expected);
    }

    @Test
    public void testDeprecatedProperties()
    {
        Map<String, String> currentProperties = new ImmutableMap.Builder<String, String>()
                .put("node.environment", "environment")
                .put("node.ip", "1.2.3.4")
                .build();

        Map<String, String> httpProperties = new ImmutableMap.Builder<String, String>()
                .put("node.environment", "environment")
                .put("http-server.ip", "1.2.3.4")
                .build();

        Map<String, String> jettyProperties = new ImmutableMap.Builder<String, String>()
                .put("node.environment", "environment")
                .put("jetty.ip", "1.2.3.4")
                .build();

        ConfigAssertions.assertDeprecatedEquivalence(NodeConfig.class, currentProperties, httpProperties, jettyProperties);
    }

    @Test
    public void testValidations()
    {
        assertValidates(new NodeConfig()
                .setEnvironment("test")
                .setNodeId(UUID.randomUUID().toString()));

        assertFailsValidation(new NodeConfig().setNodeId("abc/123"), "nodeId", "is malformed", Pattern.class);

        assertFailsValidation(new NodeConfig(), "environment", "may not be null", NotNull.class);
        assertFailsValidation(new NodeConfig().setEnvironment("FOO"), "environment", "is malformed", Pattern.class);

        assertFailsValidation(new NodeConfig().setPool("FOO"), "pool", "is malformed", Pattern.class);
    }
}
