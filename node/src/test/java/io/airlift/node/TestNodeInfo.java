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

import com.google.common.base.VerifyException;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.net.InetAddresses;
import io.airlift.node.NodeInfo.NodeAddresses;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.UncheckedIOException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Map;

import static com.google.common.io.Resources.getResource;
import static io.airlift.node.NodeConfig.AddressSource.FQDN;
import static io.airlift.node.NodeConfig.AddressSource.HOSTNAME;
import static io.airlift.node.NodeConfig.AddressSource.IP;
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

        NodeInfo nodeInfo = new NodeInfo(ENVIRONMENT, POOL, nodeId, internalIp, bindIp, externalAddress, location, binarySpec, configSpec, IP, null, null, false);
        assertThat(nodeInfo.getEnvironment()).isEqualTo(ENVIRONMENT);
        assertThat(nodeInfo.getPool()).isEqualTo(POOL);
        assertThat(nodeInfo.getNodeId()).isEqualTo(nodeId);
        assertThat(nodeInfo.getLocation()).isEqualTo(location);
        assertThat(nodeInfo.getBinarySpec()).isEqualTo(binarySpec);
        assertThat(nodeInfo.getConfigSpec()).isEqualTo(configSpec);
        assertThat(nodeInfo.getInstanceId()).isNotNull();

        assertThat(nodeInfo.getNodeId()).isNotEqualTo(nodeInfo.getInstanceId());

        assertThat(nodeInfo.getInternalAddress()).isEqualTo(internalIp);
        assertThat(nodeInfo.getExternalAddress()).isEqualTo(externalAddress);
        assertThat(nodeInfo.getBindIp()).isEqualTo(bindIp);
        assertThat(nodeInfo.getStartTime()).isGreaterThanOrEqualTo(testStartTime);
        assertThat(nodeInfo.getAnnotations().size()).isEqualTo(0);

        // make sure toString doesn't throw an exception
        assertThat(nodeInfo.toString()).isNotNull();
    }

    @Test
    public void testDefaultAddresses()
    {
        NodeInfo nodeInfo = new NodeInfo(ENVIRONMENT, POOL, "nodeInfo", "10.0.0.22", null, null, null, null, null, IP, null, null, false);
        assertThat(nodeInfo.getExternalAddress()).isEqualTo("10.0.0.22");
        assertThat(nodeInfo.getBindIp()).isEqualTo(InetAddresses.forString("0.0.0.0"));
    }

    @Test
    public void testIpDiscovery()
    {
        NodeInfo nodeInfo = new NodeInfo(ENVIRONMENT, POOL, "nodeInfo", null, null, null, null, null, null, IP, null, null, false);
        assertThat(nodeInfo.getInternalAddress()).isNotNull();
        assertThat(nodeInfo.getBindIp()).isEqualTo(InetAddresses.forString("0.0.0.0"));
        assertThat(nodeInfo.getExternalAddress()).isEqualTo(nodeInfo.getInternalAddress());
    }

    @Test
    public void testIpDiscoveryIpv6Preferred()
    {
        NodeInfo nodeInfo = new NodeInfo(ENVIRONMENT, POOL, "nodeInfo", null, null, null, null, null, null, IP, null, null, true);
        assertThat(nodeInfo.getInternalAddress()).isNotNull();
        assertThat(nodeInfo.getBindIp()).isEqualTo(InetAddresses.forString("0.0.0.0"));
        assertThat(nodeInfo.getExternalAddress()).isEqualTo(nodeInfo.getInternalAddress());
    }

    @Test
    public void testHostnameDiscovery()
            throws UnknownHostException
    {
        NodeInfo nodeInfo = new NodeInfo(ENVIRONMENT, POOL, "nodeInfo", null, null, null, null, null, null, HOSTNAME, null, null, false);
        assertThat(nodeInfo.getInternalAddress()).isNotNull();
        assertThat(nodeInfo.getBindIp()).isEqualTo(InetAddresses.forString("0.0.0.0"));
        assertThat(nodeInfo.getExternalAddress()).isEqualTo(InetAddress.getLocalHost().getHostName());
    }

    @Test
    public void testFqdnDiscovery()
            throws UnknownHostException
    {
        NodeInfo nodeInfo = new NodeInfo(ENVIRONMENT, POOL, "nodeInfo", null, null, null, null, null, null, FQDN, null, null, false);
        assertThat(nodeInfo.getInternalAddress()).isNotNull();
        assertThat(nodeInfo.getBindIp()).isEqualTo(InetAddresses.forString("0.0.0.0"));
        assertThat(nodeInfo.getExternalAddress()).isEqualTo(InetAddress.getLocalHost().getCanonicalHostName());
    }

    @Test
    public void testAnnotationFile()
            throws URISyntaxException
    {
        String annotationFile = new File(getResource("annotations.properties").toURI()).getAbsolutePath();
        NodeInfo nodeInfo = new NodeInfo(ENVIRONMENT, POOL, "nodeInfo", null, null, null, null, null, null, IP, annotationFile, null, false);
        assertThat(nodeInfo.getAnnotations()).isNotNull();
        assertThat(nodeInfo.getAnnotations()).isEqualTo(ImmutableMap.of("team", "a", "region", "b"));
    }

    @Test
    public void testAnnotations()
    {
        Map<String, String> annotations = ImmutableMap.of("team", "a", "region", "b");
        NodeInfo nodeInfo = new NodeInfo(ENVIRONMENT, POOL, "nodeInfo", null, null, null, null, null, null, IP, null, annotations, false);
        assertThat(nodeInfo.getAnnotations()).isNotNull();
        assertThat(nodeInfo.getAnnotations()).isEqualTo(annotations);
    }

    @Test
    public void testInvalidNodeId()
    {
        assertThatThrownBy(() -> new NodeInfo(ENVIRONMENT, POOL, "abc/123", null, null, null, null, null, null, IP, null, null, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageMatching("nodeId .*");
    }

    @Test
    public void testInvalidEnvironment()
    {
        assertThatThrownBy(() -> new NodeInfo("ENV", POOL, null, null, null, null, null, null, null, IP, null, null, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageMatching("environment .*");
    }

    @Test
    public void testInvalidPool()
    {
        assertThatThrownBy(() -> new NodeInfo(ENVIRONMENT, "POOL", null, null, null, null, null, null, null, IP, null, null, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageMatching("pool .*");
    }

    @Test
    public void testInvalidAnnotationFile()
    {
        assertThatThrownBy(() -> new NodeInfo(ENVIRONMENT, POOL, "nodeInfo", null, null, null, null, null, null, IP, "invalid.file", null, false))
                .isInstanceOf(UncheckedIOException.class)
                .hasMessageMatching("java.io.FileNotFoundException: invalid.file \\(No such file or directory\\)");
    }

    @Test
    public void testAnnotationFileAndAnnotations()
    {
        assertThatThrownBy(() -> new NodeInfo(ENVIRONMENT, POOL, "nodeInfo", null, null, null, null, null, null, IP, "invalid.file", Map.of(), false))
                .isInstanceOf(VerifyException.class)
                .hasMessageMatching("Only one of annotationFile or annotations should be set, but not both");
    }

    @Test
    public void testIpDiscoveryLocalHostPreferred()
    {
        InetAddress localHostIpv4 = Inet4Address.ofLiteral("10.1.2.3");
        InetAddress localHostIpv6 = Inet6Address.ofLiteral("10::1234");
        InetAddress otherIpv4 = Inet4Address.ofLiteral("10.1.2.4");
        InetAddress otherIpv6 = Inet6Address.ofLiteral("10::1231");
        // prefer ipv4
        testInternalAddressDiscovery(false, localHostIpv4, ImmutableList.of(otherIpv6, otherIpv4, localHostIpv4), localHostIpv4);

        // localHost not on the list of good addresses (this is the case for loopback addresses)
        testInternalAddressDiscovery(false, localHostIpv4, ImmutableList.of(otherIpv6, otherIpv4), otherIpv4);

        // prefer ipv4 over ipv6 local host
        testInternalAddressDiscovery(false, localHostIpv6, ImmutableList.of(localHostIpv6, otherIpv4), otherIpv4);
        // prefer ipv4 but no ipv4 ip available
        testInternalAddressDiscovery(false, localHostIpv6, ImmutableList.of(localHostIpv6, otherIpv6), localHostIpv6);

        testInternalAddressDiscovery(false, localHostIpv6, ImmutableList.of(), Inet4Address.ofLiteral("127.0.0.1"));
        // prefer ipv6
        testInternalAddressDiscovery(true, localHostIpv4, ImmutableList.of(otherIpv6, otherIpv4, localHostIpv4), otherIpv6);
        testInternalAddressDiscovery(true, localHostIpv6, ImmutableList.of(otherIpv6, otherIpv4, localHostIpv6), localHostIpv6);

        // prefer ipv6 over ipv4 local host
        testInternalAddressDiscovery(true, localHostIpv4, ImmutableList.of(localHostIpv4, otherIpv6), otherIpv6);
        // prefer ipv4 but no ipv4 ip available
        testInternalAddressDiscovery(true, localHostIpv4, ImmutableList.of(localHostIpv4, otherIpv4), localHostIpv4);

        // no good addresses, return ipv4 loopback address, even if ipv6 is preferred
        testInternalAddressDiscovery(true, localHostIpv6, ImmutableList.of(), Inet4Address.ofLiteral("127.0.0.1"));

        NodeAddresses throwingNetworkAddresses = new NodeAddresses()
        {
            @Override
            public List<InetAddress> getAddresses()
            {
                return ImmutableList.of(otherIpv4, otherIpv6);
            }

            @Override
            public InetAddress getLocalHost()
                    throws UnknownHostException
            {
                throw new UnknownHostException();
            }
        };

        NodeInfo nodeInfoIpv4 = new NodeInfo(ENVIRONMENT, POOL, "nodeInfo", null, null, null, null, null, null, IP, null, null, false, throwingNetworkAddresses);

        assertThat(nodeInfoIpv4.getInternalAddress()).isEqualTo(InetAddresses.toAddrString(otherIpv4));

        NodeInfo nodeInfoIpv6 = new NodeInfo(ENVIRONMENT, POOL, "nodeInfo", null, null, null, null, null, null, IP, null, null, true, throwingNetworkAddresses);

        assertThat(nodeInfoIpv6.getInternalAddress()).isEqualTo(InetAddresses.toAddrString(otherIpv6));
    }

    private static void testInternalAddressDiscovery(boolean preferIpv6Address, InetAddress localHost, List<InetAddress> addresses, InetAddress expectedInternalAddress)
    {
        NodeAddresses networkAddresses = new NodeAddresses()
        {
            @Override
            public List<InetAddress> getAddresses()
            {
                return addresses;
            }

            @Override
            public InetAddress getLocalHost()
            {
                return localHost;
            }
        };
        NodeInfo nodeInfo = new NodeInfo(ENVIRONMENT, POOL, "nodeInfo", null, null, null, null, null, null, IP, null, null, preferIpv6Address, networkAddresses);

        assertThat(nodeInfo.getInternalAddress()).isEqualTo(InetAddresses.toAddrString(expectedInternalAddress));
    }
}
