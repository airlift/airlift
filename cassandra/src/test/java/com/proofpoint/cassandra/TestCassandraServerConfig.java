package com.proofpoint.cassandra;

import com.google.common.collect.ImmutableMap;
import com.proofpoint.configuration.testing.ConfigAssertions;
import com.proofpoint.experimental.units.DataSize;
import org.testng.annotations.Test;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import java.io.File;
import java.util.Map;

import static com.proofpoint.experimental.testing.ValidationAssertions.assertFailsValidation;

public class TestCassandraServerConfig
{
    @Test
    public void testDefaults()
    {
        ConfigAssertions.assertRecordedDefaults(ConfigAssertions.recordDefaults(CassandraServerConfig.class)
                                                        .setClusterName("cluster")
                                                        .setDirectory(null)
                                                        .setSeeds(null)
                                                        .setRpcPort(9160)
                                                        .setStoragePort(7000)
                                                        .setInMemoryCompactionLimit(new DataSize(8, DataSize.Unit.MEGABYTE))
                                                        .setColumnIndexSize(new DataSize(16, DataSize.Unit.KILOBYTE)));
    }

    @Test
    public void testExplicitPropertyMappings()
    {
        Map<String, String> properties = ImmutableMap.<String, String>builder()
                .put("cassandra.cluster-name", "megacluster")
                .put("cassandra.directory", "/tmp")
                .put("cassandra.seeds", "10.0.0.1")
                .put("cassandra.rpc-port", "1500")
                .put("cassandra.storage-port", "1501")
                .put("cassandra.in-memory-compaction-limit", "64 MB")
                .put("cassandra.column-index-size", "30 kB")
                .build();

        CassandraServerConfig expected = new CassandraServerConfig()
                .setClusterName("megacluster")
                .setDirectory(new File("/tmp"))
                .setSeeds("10.0.0.1")
                .setRpcPort(1500)
                .setStoragePort(1501)
                .setInMemoryCompactionLimit(new DataSize(64, DataSize.Unit.MEGABYTE))
                .setColumnIndexSize(new DataSize(30, DataSize.Unit.KILOBYTE));

        ConfigAssertions.assertFullMapping(properties, expected);
    }

    @Test
    public void testValidatesMaxRpcPort()
    {
        CassandraServerConfig config = new CassandraServerConfig()
                .setRpcPort(65537);

        assertFailsValidation(config, "rpcPort", "must be less than or equal to 65535", Max.class);
    }

    @Test
    public void testValidatesMaxStoragePort()
    {
        CassandraServerConfig config = new CassandraServerConfig()
                .setStoragePort(65537);

        assertFailsValidation(config, "storagePort", "must be less than or equal to 65535", Max.class);
    }

    @Test
    public void testValidatesMinRpcPort()
    {
        CassandraServerConfig config = new CassandraServerConfig()
                .setRpcPort(0);

        assertFailsValidation(config, "rpcPort", "must be greater than or equal to 1", Min.class);
    }

    @Test
    public void testValidatesMinStoragePort()
    {
        CassandraServerConfig config = new CassandraServerConfig()
                .setStoragePort(0);

        assertFailsValidation(config, "storagePort", "must be greater than or equal to 1", Min.class);
    }

    @Test
    public void testValidatesNotNullDirectory()
    {
        CassandraServerConfig config = new CassandraServerConfig()
                .setDirectory(null);

        assertFailsValidation(config, "directory", "may not be null", NotNull.class);
    }

    @Test
    public void testValidatesNotNullSeeds()
    {
        CassandraServerConfig config = new CassandraServerConfig()
                .setSeeds(null);

        assertFailsValidation(config, "seeds", "may not be null", NotNull.class);
    }

    @Test
    public void testValidatesNotNullInMemoryCompactionLimit()
    {
        CassandraServerConfig config = new CassandraServerConfig()
                .setInMemoryCompactionLimit(null);

        assertFailsValidation(config, "inMemoryCompactionLimit", "may not be null", NotNull.class);
    }

    @Test
    public void testValidatesNotNullColumnIndexSize()
    {
        CassandraServerConfig config = new CassandraServerConfig()
                .setColumnIndexSize(null);

        assertFailsValidation(config, "columnIndexSize", "may not be null", NotNull.class);
    }

}
