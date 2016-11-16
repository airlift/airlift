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
import io.airlift.configuration.Config;
import io.airlift.configuration.DefunctConfig;
import io.airlift.configuration.LegacyConfig;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;

import java.net.InetAddress;

@DefunctConfig({"http-server.ip", "jetty.ip"})
public class NodeConfig
{
    public static final String ID_REGEXP = "[A-Za-z0-9][_A-Za-z0-9-]*";
    public static final String ENV_REGEXP = "[a-z0-9][_a-z0-9]*";
    public static final String POOL_REGEXP = "[a-z0-9][_a-z0-9]*";

    private String environment;
    private String pool = "general";
    private String nodeId;
    private String location;
    private String nodeInternalAddress;
    private String nodeExternalAddress;
    private InetAddress nodeBindIp;
    private String binarySpec;
    private String configSpec;
    private AddressSource internalAddressSource = AddressSource.IP;

    @NotNull
    @Pattern(regexp = ENV_REGEXP, message = "is malformed")
    public String getEnvironment()
    {
        return environment;
    }

    @Config("node.environment")
    public NodeConfig setEnvironment(String environment)
    {
        this.environment = environment;
        return this;
    }

    @NotNull
    @Pattern(regexp = POOL_REGEXP, message = "is malformed")
    public String getPool()
    {
        return pool;
    }

    @Config("node.pool")
    public NodeConfig setPool(String pool)
    {
        this.pool = pool;
        return this;
    }

    @Pattern(regexp = ID_REGEXP, message = "is malformed")
    public String getNodeId()
    {
        return nodeId;
    }

    @Config("node.id")
    public NodeConfig setNodeId(String nodeId)
    {
        this.nodeId = nodeId;
        return this;
    }

    public String getLocation()
    {
        return location;
    }

    @Config("node.location")
    public NodeConfig setLocation(String location)
    {
        this.location = location;
        return this;
    }

    public String getNodeInternalAddress()
    {
        return nodeInternalAddress;
    }

    @Config("node.internal-address")
    @LegacyConfig("node.ip")
    public NodeConfig setNodeInternalAddress(String nodeInternalAddress)
    {
        this.nodeInternalAddress = nodeInternalAddress;
        return this;
    }

    public String getNodeExternalAddress()
    {
        return nodeExternalAddress;
    }

    @Config("node.external-address")
    public NodeConfig setNodeExternalAddress(String nodeExternalAddress)
    {
        this.nodeExternalAddress = nodeExternalAddress;
        return this;
    }

    public InetAddress getNodeBindIp()
    {
        return nodeBindIp;
    }

    public NodeConfig setNodeBindIp(InetAddress nodeBindIp)
    {
        this.nodeBindIp = nodeBindIp;
        return this;
    }

    @Config("node.bind-ip")
    public NodeConfig setNodeBindIp(String nodeBindIp)
    {
        if (nodeBindIp != null) {
            this.nodeBindIp = InetAddresses.forString(nodeBindIp);
        }
        return this;
    }

    public String getBinarySpec()
    {
        return binarySpec;
    }

    @Config("node.binary-spec")
    public NodeConfig setBinarySpec(String binarySpec)
    {
        this.binarySpec = binarySpec;
        return this;
    }

    public String getConfigSpec()
    {
        return configSpec;
    }

    @Config("node.config-spec")
    public NodeConfig setConfigSpec(String configSpec)
    {
        this.configSpec = configSpec;
        return this;
    }

    public AddressSource getInternalAddressSource()
    {
        return internalAddressSource;
    }

    @Config("node.internal-address-source")
    public NodeConfig setInternalAddressSource(AddressSource internalAddressSource)
    {
        this.internalAddressSource = internalAddressSource;
        return this;
    }

    public enum AddressSource
    {
        HOSTNAME, FQDN, IP
    }
}
