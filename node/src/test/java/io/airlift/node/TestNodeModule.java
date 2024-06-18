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

import java.io.File;
import java.net.InetAddress;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Map;

import static com.google.common.io.Resources.getResource;
import static io.airlift.testing.Assertions.assertGreaterThanOrEqual;
import static io.airlift.testing.Assertions.assertNotEquals;
import static org.assertj.core.api.Assertions.assertThat;

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
        assertThat(nodeInfo).isNotNull();
        assertThat(nodeInfo.getEnvironment()).isEqualTo("environment");
        assertThat(nodeInfo.getPool()).isEqualTo("general");
        assertThat(nodeInfo.getNodeId()).isNotNull();
        assertThat(nodeInfo.getLocation()).isNotNull();
        assertThat(nodeInfo.getBinarySpec()).isNull();
        assertThat(nodeInfo.getConfigSpec()).isNull();
        assertThat(nodeInfo.getInstanceId()).isNotNull();

        assertNotEquals(nodeInfo.getNodeId(), nodeInfo.getInstanceId());

        assertThat(nodeInfo.getInternalAddress()).isNotNull();
        assertThat(InetAddress.getByName(nodeInfo.getInternalAddress()).isAnyLocalAddress()).isFalse();
        assertThat(nodeInfo.getBindIp()).isNotNull();
        assertThat(nodeInfo.getBindIp().isAnyLocalAddress()).isTrue();
        assertGreaterThanOrEqual(nodeInfo.getStartTime(), testStartTime);
        assertThat(nodeInfo.getAnnotations().size()).isEqualTo(0);

        // make sure toString doesn't throw an exception
        assertThat(nodeInfo.toString()).isNotNull();
    }

    @Test
    public void testFullConfig()
            throws URISyntaxException
    {
        long testStartTime = System.currentTimeMillis();

        String environment = "environment";
        String pool = "pool";
        String nodeId = "nodeId";
        String location = "location";
        String binarySpec = "binary";
        String configSpec = "config";
        String publicAddress = "public";
        File annotationFile = new File(getResource("annotations.properties").toURI());

        ConfigurationFactory configFactory = new ConfigurationFactory(ImmutableMap.<String, String>builder()
                .put("node.environment", environment)
                .put("node.pool", pool)
                .put("node.id", nodeId)
                .put("node.internal-address", publicAddress)
                .put("node.location", location)
                .put("node.binary-spec", binarySpec)
                .put("node.config-spec", configSpec)
                .put("node.annotation-file", annotationFile.getAbsolutePath())
                .build());

        Injector injector = Guice.createInjector(new NodeModule(), new ConfigurationModule(configFactory));
        NodeInfo nodeInfo = injector.getInstance(NodeInfo.class);
        assertThat(nodeInfo).isNotNull();
        assertThat(nodeInfo.getEnvironment()).isEqualTo(environment);
        assertThat(nodeInfo.getPool()).isEqualTo(pool);
        assertThat(nodeInfo.getNodeId()).isEqualTo(nodeId);
        assertThat(nodeInfo.getLocation()).isEqualTo(location);
        assertThat(nodeInfo.getBinarySpec()).isEqualTo(binarySpec);
        assertThat(nodeInfo.getConfigSpec()).isEqualTo(configSpec);
        assertThat(nodeInfo.getInstanceId()).isNotNull();

        assertNotEquals(nodeInfo.getNodeId(), nodeInfo.getInstanceId());

        assertThat(nodeInfo.getInternalAddress()).isEqualTo(publicAddress);
        assertThat(nodeInfo.getBindIp()).isEqualTo(InetAddresses.forString("0.0.0.0"));
        assertGreaterThanOrEqual(nodeInfo.getStartTime(), testStartTime);
        assertThat(nodeInfo.getAnnotations()).isEqualTo(Map.of("team", "a", "region", "b"));

        // make sure toString doesn't throw an exception
        assertThat(nodeInfo.toString()).isNotNull();
    }
}
