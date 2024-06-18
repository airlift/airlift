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
import org.junit.jupiter.api.Test;

import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.UnknownHostException;

import static io.airlift.node.NodeConfig.AddressSource.FQDN;
import static io.airlift.node.NodeConfig.AddressSource.HOSTNAME;
import static io.airlift.node.NodeConfig.AddressSource.IP;
import static io.airlift.testing.Assertions.assertGreaterThanOrEqual;
import static io.airlift.testing.Assertions.assertNotEquals;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

        NodeInfo nodeInfo = new NodeInfo(ENVIRONMENT, POOL, nodeId, internalIp, bindIp, externalAddress, location, binarySpec, configSpec, IP, null);
        assertThat(nodeInfo.getEnvironment()).isEqualTo(ENVIRONMENT);
        assertThat(nodeInfo.getPool()).isEqualTo(POOL);
        assertThat(nodeInfo.getNodeId()).isEqualTo(nodeId);
        assertThat(nodeInfo.getLocation()).isEqualTo(location);
        assertThat(nodeInfo.getBinarySpec()).isEqualTo(binarySpec);
        assertThat(nodeInfo.getConfigSpec()).isEqualTo(configSpec);
        assertThat(nodeInfo.getInstanceId()).isNotNull();

        assertNotEquals(nodeInfo.getNodeId(), nodeInfo.getInstanceId());

        assertThat(nodeInfo.getInternalAddress()).isEqualTo(internalIp);
        assertThat(nodeInfo.getExternalAddress()).isEqualTo(externalAddress);
        assertThat(nodeInfo.getBindIp()).isEqualTo(bindIp);
        assertGreaterThanOrEqual(nodeInfo.getStartTime(), testStartTime);
        assertThat(nodeInfo.getAnnotations().size()).isEqualTo(0);

        // make sure toString doesn't throw an exception
        assertThat(nodeInfo.toString()).isNotNull();
    }

    @Test
    public void testDefaultAddresses()
    {
        NodeInfo nodeInfo = new NodeInfo(ENVIRONMENT, POOL, "nodeInfo", "10.0.0.22", null, null, null, null, null, IP, null);
        assertThat(nodeInfo.getExternalAddress()).isEqualTo("10.0.0.22");
        assertThat(nodeInfo.getBindIp()).isEqualTo(InetAddresses.forString("0.0.0.0"));
    }

    @Test
    public void testIpDiscovery()
            throws UnknownHostException
    {
        NodeInfo nodeInfo = new NodeInfo(ENVIRONMENT, POOL, "nodeInfo", null, null, null, null, null, null, IP, null);
        assertThat(nodeInfo.getInternalAddress()).isNotNull();
        assertThat(nodeInfo.getBindIp()).isEqualTo(InetAddresses.forString("0.0.0.0"));
        assertThat(nodeInfo.getExternalAddress()).isEqualTo(nodeInfo.getInternalAddress());
    }

    @Test
    public void testHostnameDiscovery()
            throws UnknownHostException
    {
        NodeInfo nodeInfo = new NodeInfo(ENVIRONMENT, POOL, "nodeInfo", null, null, null, null, null, null, HOSTNAME, null);
        assertThat(nodeInfo.getInternalAddress()).isNotNull();
        assertThat(nodeInfo.getBindIp()).isEqualTo(InetAddresses.forString("0.0.0.0"));
        assertThat(nodeInfo.getExternalAddress()).isEqualTo(InetAddress.getLocalHost().getHostName());
    }

    @Test
    public void testFqdnDiscovery()
            throws UnknownHostException
    {
        NodeInfo nodeInfo = new NodeInfo(ENVIRONMENT, POOL, "nodeInfo", null, null, null, null, null, null, FQDN, null);
        assertThat(nodeInfo.getInternalAddress()).isNotNull();
        assertThat(nodeInfo.getBindIp()).isEqualTo(InetAddresses.forString("0.0.0.0"));
        assertThat(nodeInfo.getExternalAddress()).isEqualTo(InetAddress.getLocalHost().getCanonicalHostName());
    }

    @Test
    public void testInvalidNodeId()
    {
        assertThatThrownBy(() -> new NodeInfo(ENVIRONMENT, POOL, "abc/123", null, null, null, null, null, null, IP, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageMatching("nodeId .*");
    }

    @Test
    public void testInvalidEnvironment()
    {
        assertThatThrownBy(() -> new NodeInfo("ENV", POOL, null, null, null, null, null, null, null, IP, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageMatching("environment .*");
    }

    @Test
    public void testInvalidPool()
    {
        assertThatThrownBy(() -> new NodeInfo(ENVIRONMENT, "POOL", null, null, null, null, null, null, null, IP, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageMatching("pool .*");
    }

    @Test
    public void testInvalidAnnotationFile()
    {
        assertThatThrownBy(() -> new NodeInfo(ENVIRONMENT, POOL, "nodeInfo", null, null, null, null, null, null, IP, "invalid.file"))
                .isInstanceOf(UncheckedIOException.class)
                .hasMessageMatching("java.io.FileNotFoundException: invalid.file \\(No such file or directory\\)");
    }
}
