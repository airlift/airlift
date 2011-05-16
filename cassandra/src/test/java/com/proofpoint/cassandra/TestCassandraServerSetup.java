package com.proofpoint.cassandra;

import com.google.inject.Binder;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.proofpoint.cassandra.testing.CassandraServerSetup;
import com.proofpoint.cassandra.testing.TestingCassandraModule;
import org.apache.cassandra.config.ConfigurationException;
import org.apache.thrift.transport.TTransportException;
import org.testng.annotations.AfterSuite;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.Test;

import java.io.IOException;

import static com.proofpoint.testing.Assertions.assertNotEquals;

public class TestCassandraServerSetup
{
    @BeforeSuite
    public void setup()
            throws IOException, TTransportException, ConfigurationException, InterruptedException
    {
        CassandraServerSetup.tryInitialize();
    }

    @Test
    public void testServerInitializes()
    {
        CassandraServerInfo serverInfo = CassandraServerSetup.getServerInfo();
        assertNotEquals(serverInfo.getRpcPort(), 0);
    }

    @Test
    public void testTestingCassandraModule()
    {
        Injector injector = Guice.createInjector(new TestingCassandraModule());
        CassandraServerInfo info = injector.getInstance(CassandraServerInfo.class);
        assertNotEquals(info.getRpcPort(), 0);
    }

    // TODO: add test that connects to embedded cassandra, creates keyspace, etc

    @AfterSuite
    public void teardown()
            throws IOException
    {
        CassandraServerSetup.tryShutdown();
    }
}
