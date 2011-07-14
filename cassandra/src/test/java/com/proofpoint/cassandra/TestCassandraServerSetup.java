package com.proofpoint.cassandra;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.proofpoint.cassandra.testing.CassandraServerSetup;
import com.proofpoint.cassandra.testing.TestingCassandraModule;
import me.prettyprint.cassandra.model.AllOneConsistencyLevelPolicy;
import me.prettyprint.cassandra.serializers.StringSerializer;
import me.prettyprint.cassandra.service.CassandraHostConfigurator;
import me.prettyprint.cassandra.service.ThriftCfDef;
import me.prettyprint.cassandra.service.ThriftKsDef;
import me.prettyprint.cassandra.service.template.ColumnFamilyResult;
import me.prettyprint.cassandra.service.template.ColumnFamilyTemplate;
import me.prettyprint.cassandra.service.template.ColumnFamilyUpdater;
import me.prettyprint.cassandra.service.template.ThriftColumnFamilyTemplate;
import me.prettyprint.hector.api.Cluster;
import me.prettyprint.hector.api.Keyspace;
import me.prettyprint.hector.api.factory.HFactory;
import org.apache.cassandra.config.ConfigurationException;
import org.apache.cassandra.dht.RandomPartitioner;
import org.apache.thrift.transport.TTransportException;
import org.testng.annotations.AfterSuite;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.Arrays;

import static com.proofpoint.testing.Assertions.assertNotEquals;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

public class TestCassandraServerSetup
{
    private Cluster cluster;

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

    @Test
    public void testConnect()
    {
        setupCluster();
        assertEquals(cluster.describeClusterName(), "megacluster");
        assertEquals(cluster.describePartitioner(), RandomPartitioner.class.getName());
    }

    @Test
    public void testCreateReadWrite()
    {
        String ksName = "testing";
        String cfName = "abc";
        String key = "12345";

        // create keyspace
        assertNull(cluster.describeKeyspace(ksName));
        cluster.addKeyspace(new ThriftKsDef(ksName));

        // create column family
        assertTrue(cluster.describeKeyspace(ksName).getCfDefs().isEmpty());
        cluster.addColumnFamily(new ThriftCfDef(ksName, cfName));

        assertEquals(cluster.describeKeyspace(ksName).getCfDefs().size(), 1);
        assertEquals(cluster.describeKeyspace(ksName).getCfDefs().get(0).getName(), "abc");

        // get keyspace object
        Keyspace keyspace = HFactory.createKeyspace(ksName, cluster);
        keyspace.setConsistencyLevelPolicy(new AllOneConsistencyLevelPolicy());

        // get column family template
        ColumnFamilyTemplate<String, String> template = new ThriftColumnFamilyTemplate<String, String>(
                keyspace, cfName, StringSerializer.get(), StringSerializer.get());

        // put data
        ColumnFamilyUpdater<String, String> updater = template.createUpdater(key);
        updater.setString("domain", "www.proofpoint.com");
        updater.setLong("time", System.currentTimeMillis());
        template.update(updater);

        // get data
        ColumnFamilyResult<String, String> res = template.queryColumns(key);
        assertEquals(res.getColumnNames(), Arrays.asList("domain", "time"));
        assertEquals(res.getString("domain"), "www.proofpoint.com");

        // delete column data
        template.deleteColumn(key, "domain");
        assertEquals(template.queryColumns(key).getColumnNames(), Arrays.asList("time"));

        // delete row data
        assertTrue(template.isColumnsExist(key));
        template.deleteRow(key);
        assertFalse(template.isColumnsExist(key));
    }

    @AfterSuite
    public void teardown()
            throws IOException
    {
        HFactory.shutdownCluster(cluster);
        CassandraServerSetup.tryShutdown();
    }

    private void setupCluster()
    {
        CassandraServerInfo serverInfo = CassandraServerSetup.getServerInfo();
        CassandraHostConfigurator configurator = new CassandraHostConfigurator(serverInfo.getRpcHost());
        cluster = HFactory.getOrCreateCluster("testing", configurator);
    }
}
