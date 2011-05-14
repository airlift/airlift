package com.proofpoint.cassandra;

import com.proofpoint.configuration.Config;
import com.proofpoint.experimental.units.DataSize;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import java.io.File;

public class CassandraServerConfig
{
    private String clusterName = "cluster";
    private File directory;
    private int rpcPort = 9160;
    private int storagePort = 7000;
    private String seeds;
    private DataSize inMemoryCompactionLimit = new DataSize(8, DataSize.Unit.MEGABYTE);
    private DataSize columnIndexSize = new DataSize(16, DataSize.Unit.KILOBYTE);


    @Config("cassandra.cluster-name")
    public CassandraServerConfig setClusterName(String clusterName)
    {
        this.clusterName = clusterName;
        return this;
    }

    @NotNull
    public String getClusterName()
    {
        return clusterName;
    }


    @Config("cassandra.directory")
    public CassandraServerConfig setDirectory(File directory)
    {
        this.directory = directory;
        return this;
    }

    @NotNull
    public File getDirectory()
    {
        return directory;
    }

    @Config("cassandra.rpc-port")
    public CassandraServerConfig setRpcPort(int rpcPort)
    {
        this.rpcPort = rpcPort;
        return this;
    }

    @Min(1)
    @Max(65535)
    public int getRpcPort()
    {
        return rpcPort;
    }

    @Config("cassandra.storage-port")
    public CassandraServerConfig setStoragePort(int storagePort)
    {
        this.storagePort = storagePort;
        return this;
    }

    @Min(1)
    @Max(65535)
    public int getStoragePort()
    {
        return storagePort;
    }

    @Config("cassandra.seeds")
    public CassandraServerConfig setSeeds(String seeds)
    {
        this.seeds = seeds;
        return this;
    }

    @NotNull
    public String getSeeds()
    {
        return seeds;
    }


    @Config("cassandra.in-memory-compaction-limit")
    public CassandraServerConfig setInMemoryCompactionLimit(DataSize inMemoryCompactionLimit)
    {
        this.inMemoryCompactionLimit = inMemoryCompactionLimit;
        return this;
    }

    @NotNull
    public DataSize getInMemoryCompactionLimit()
    {
        return inMemoryCompactionLimit;
    }

    @Config("cassandra.column-index-size")
    public CassandraServerConfig setColumnIndexSize(DataSize columnIndexSize)
    {
        this.columnIndexSize = columnIndexSize;
        return this;
    }

    @NotNull
    public DataSize getColumnIndexSize()
    {
        return columnIndexSize;
    }
}
