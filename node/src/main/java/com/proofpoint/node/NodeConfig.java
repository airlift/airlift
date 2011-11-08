package com.proofpoint.node;

import com.google.common.net.InetAddresses;
import com.proofpoint.configuration.Config;
import com.proofpoint.configuration.LegacyConfig;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import java.net.InetAddress;

public class NodeConfig
{
    public static final String ENV_REGEXP = "[a-z0-9][_a-z0-9]*";
    public static final String POOL_REGEXP = "[a-z0-9][_a-z0-9]*";

    private String environment;
    private String pool = "general";
    private String nodeId;
    private String location;
    private InetAddress nodeIp;
    private String binarySpec;
    private String configSpec;

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

    public InetAddress getNodeIp()
    {
        return nodeIp;
    }

    public NodeConfig setNodeIp(InetAddress nodeIp)
    {
        this.nodeIp = nodeIp;
        return this;
    }

    @Config("node.ip")
    @LegacyConfig({"http-server.ip", "jetty.ip"})
    public NodeConfig setNodeIp(String nodeIp)
    {
        if (nodeIp != null) {
            this.nodeIp = InetAddresses.forString(nodeIp);
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
}
