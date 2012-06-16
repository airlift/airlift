package com.proofpoint.cassandra.testing;

import com.google.inject.Binder;
import com.google.inject.Module;
import com.proofpoint.cassandra.CassandraServerInfo;

public class TestingCassandraModule
    implements Module
{
    @Override
    public void configure(Binder binder)
    {
        binder.bind(CassandraServerInfo.class).toInstance(CassandraServerSetup.getServerInfo());
    }
}
