package com.proofpoint.cassandra;

import javax.inject.Inject;
import javax.inject.Provider;

class CassandraServerInfoProvider
    implements Provider<CassandraServerInfo>
{
    private final int rpcPort;

    @Inject
    public CassandraServerInfoProvider(EmbeddedCassandraServer server, CassandraServerConfig config)
    {
        // note: depend on EmbeddedCassandraServer so that the server is guaranteed to be running before CassandraServerInfo
        // is provided to clients

        this.rpcPort = config.getRpcPort();
    }

    @Override
    public CassandraServerInfo get()
    {
        return new CassandraServerInfo(rpcPort);
    }
}
