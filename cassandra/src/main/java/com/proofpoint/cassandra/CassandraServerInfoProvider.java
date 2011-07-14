package com.proofpoint.cassandra;

import javax.inject.Inject;
import javax.inject.Provider;
import java.net.InetAddress;

class CassandraServerInfoProvider
    implements Provider<CassandraServerInfo>
{
    private final InetAddress rpcAddress;
    private final int rpcPort;

    @Inject
    public CassandraServerInfoProvider(EmbeddedCassandraServer server)
    {
        // note: depend on EmbeddedCassandraServer so that the server is guaranteed to be running before CassandraServerInfo
        // is provided to clients

        this.rpcAddress = server.getRpcAddress();
        this.rpcPort = server.getRpcPort();
    }

    @Override
    public CassandraServerInfo get()
    {
        return new CassandraServerInfo(rpcAddress, rpcPort);
    }
}
