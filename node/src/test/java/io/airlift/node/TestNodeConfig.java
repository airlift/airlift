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
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.testng.annotations.Test;

import java.io.File;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.UUID;

import static com.google.common.io.Resources.getResource;
import static io.airlift.node.NodeConfig.AddressSource.HOSTNAME;
import static io.airlift.node.NodeConfig.AddressSource.IP;
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
                .setNodeInternalAddress(null)
                .setNodeBindIp((String) null)
                .setNodeExternalAddress(null)
                .setLocation(null)
                .setBinarySpec(null)
                .setConfigSpec(null)
                .setInternalAddressSource(IP)
                .setAnnotationFile(null));
    }

    @Test
    public void testExplicitPropertyMappings()
            throws URISyntaxException
    {
        File annotationFile = new File(getResource("annotations.properties").toURI());
        Map<String, String> properties = new ImmutableMap.Builder<String, String>()
                .put("node.environment", "environment")
                .put("node.pool", "pool")
                .put("node.id", "nodeId")
                .put("node.internal-address", "internal")
                .put("node.bind-ip", "10.11.12.13")
                .put("node.external-address", "external")
                .put("node.location", "location")
                .put("node.binary-spec", "binary")
                .put("node.config-spec", "config")
                .put("node.internal-address-source", "HOSTNAME")
                .put("node.annotation-file", annotationFile.getAbsolutePath())
                .build();

        NodeConfig expected = new NodeConfig()
                .setEnvironment("environment")
                .setPool("pool")
                .setNodeId("nodeId")
                .setNodeInternalAddress("internal")
                .setNodeBindIp(InetAddresses.forString("10.11.12.13"))
                .setNodeExternalAddress("external")
                .setLocation("location")
                .setBinarySpec("binary")
                .setConfigSpec("config")
                .setInternalAddressSource(HOSTNAME)
                .setAnnotationFile(annotationFile.getAbsolutePath());

        ConfigAssertions.assertFullMapping(properties, expected);
    }

    @Test
    public void testValidations()
    {
        assertValidates(new NodeConfig()
                .setEnvironment("test")
                .setNodeId(UUID.randomUUID().toString()));

        assertFailsValidation(new NodeConfig().setNodeId("abc/123"), "nodeId", "is malformed", Pattern.class);

        assertFailsValidation(new NodeConfig(), "environment", "must not be null", NotNull.class);
        assertFailsValidation(new NodeConfig().setEnvironment("FOO"), "environment", "is malformed", Pattern.class);

        assertFailsValidation(new NodeConfig().setPool("FOO"), "pool", "is malformed", Pattern.class);
    }
}
