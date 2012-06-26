/*
 * Copyright 2010 Proofpoint, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.airlift.cassandra;

import io.airlift.configuration.Config;
import io.airlift.units.DataSize;
import io.airlift.units.Duration;
import org.apache.cassandra.dht.ByteOrderedPartitioner;
import org.apache.cassandra.dht.CollatingOrderPreservingPartitioner;
import org.apache.cassandra.dht.OrderPreservingPartitioner;
import org.apache.cassandra.dht.RandomPartitioner;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import java.io.File;
import java.util.concurrent.TimeUnit;

public class CassandraServerConfig
{
    private String clusterName = "cluster";
    private File directory;
    private int rpcPort = 9160;
    private int storagePort = 7000;
    private Duration rpcTimeout = new Duration(2, TimeUnit.SECONDS);
    private String seeds;
    private DataSize inMemoryCompactionLimit = new DataSize(8, DataSize.Unit.MEGABYTE);
    private DataSize columnIndexSize = new DataSize(16, DataSize.Unit.KILOBYTE);
    private Partitioner partitioner = Partitioner.RANDOM;
    private DataSize memtableTotalSpace = new DataSize(Runtime.getRuntime().maxMemory() / (3 * 1048576), DataSize.Unit.MEGABYTE);

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

    @Config("cassandra.rpc-timeout")
    public CassandraServerConfig setRpcTimeout(Duration rpcTimeout)
    {
        this.rpcTimeout = rpcTimeout;
        return this;
    }

    @NotNull
    public Duration getRpcTimeout()
    {
        return rpcTimeout;
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


    @Config("cassandra.partitioner")
    public CassandraServerConfig setPartitioner(Partitioner partitioner)
    {
        this.partitioner = partitioner;
        return this;
    }

    @NotNull
    public Partitioner getPartitioner()
    {
        return partitioner;
    }

    @Config("cassandra.memtable-total-space")
    public CassandraServerConfig setMemtableTotalSpace(DataSize memtableTotalSpace)
    {
        this.memtableTotalSpace = memtableTotalSpace;
        return this;
    }

    @NotNull
    public DataSize getMemtableTotalSpace()
    {
        return memtableTotalSpace;
    }


    public static enum Partitioner
    {
        RANDOM(RandomPartitioner.class),
        ORDER_PRESERVING(OrderPreservingPartitioner.class),
        BYTE_ORDERED(ByteOrderedPartitioner.class),
        COLLATING_ORDER_PRESERVING(CollatingOrderPreservingPartitioner.class);

        private Class clazz;

        private Partitioner(Class clazz)
        {

            this.clazz = clazz;
        }

        public Class getClazz()
        {
            return clazz;
        }
    }

}
