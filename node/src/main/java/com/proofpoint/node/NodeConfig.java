package com.proofpoint.node;

import com.google.common.net.InetAddresses;
import com.proofpoint.configuration.Config;
import com.proofpoint.configuration.LegacyConfig;

import java.net.InetAddress;

public class NodeConfig
{
    private String nodeId;
    private InetAddress nodeIp;

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
}
