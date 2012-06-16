package io.airlift.node;

import com.google.common.net.InetAddresses;
import io.airlift.configuration.Config;
import io.airlift.configuration.LegacyConfig;

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
    private InetAddress nodeInternalIp;
    private String nodeExternalAddress;
    private InetAddress nodeBindIp;
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

    public InetAddress getNodeInternalIp()
    {
        return nodeInternalIp;
    }

    public NodeConfig setNodeInternalIp(InetAddress nodeInternalIp)
    {
        this.nodeInternalIp = nodeInternalIp;
        return this;
    }

    @Config("node.ip")
    @LegacyConfig({"http-server.ip", "jetty.ip"})
    public NodeConfig setNodeInternalIp(String nodeInternalIp)
    {
        if (nodeInternalIp != null) {
            this.nodeInternalIp = InetAddresses.forString(nodeInternalIp);
        }
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
}
