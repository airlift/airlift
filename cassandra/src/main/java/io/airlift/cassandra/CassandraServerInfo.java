package io.airlift.cassandra;

import java.net.InetAddress;

public class CassandraServerInfo
{
    private final InetAddress rpcAddress;
    private final int rpcPort;

    public CassandraServerInfo(InetAddress rpcAddress, int rpcPort)
    {
        this.rpcAddress = rpcAddress;
        this.rpcPort = rpcPort;
    }

    public InetAddress getRpcAddress()
    {
        return rpcAddress;
    }

    public int getRpcPort()
    {
        return rpcPort;
    }

    public String getRpcHost()
    {
        return String.format("%s:%s", rpcAddress.getHostAddress(), rpcPort);
    }
}
