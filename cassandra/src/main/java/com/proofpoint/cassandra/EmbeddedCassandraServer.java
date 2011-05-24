package com.proofpoint.cassandra;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Files;
import com.google.common.net.InetAddresses;
import com.proofpoint.experimental.units.DataSize;
import com.proofpoint.node.NodeInfo;
import org.apache.cassandra.config.ConfigurationException;
import org.apache.cassandra.thrift.CassandraDaemon;
import org.apache.thrift.transport.TTransportException;
import org.yaml.snakeyaml.Yaml;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.util.Map;

import static java.lang.String.format;
import static java.util.Arrays.asList;

public class EmbeddedCassandraServer
{
    private final CassandraDaemon cassandra;
    private final Thread thread;

    @Inject
    public EmbeddedCassandraServer(CassandraServerConfig config, NodeInfo nodeInfo)
            throws TTransportException, IOException, InterruptedException, ConfigurationException
    {
        File directory = config.getDirectory();

        if (!directory.mkdirs() && !directory.exists()) {
            throw new IllegalStateException(format("Directory %s does not exist and cannot be created", directory));
        }

        Map<String, Object> map = ImmutableMap.<String, Object>builder()
                .put("cluster_name", config.getClusterName())
                .put("auto_bootstrap", "false")
                .put("hinted_handoff_enabled", "true")
                .put("partitioner", config.getPartitioner())
                .put("data_file_directories", asList(new File(directory, "data").getAbsolutePath()))
                .put("commitlog_directory", new File(directory, "commitlog").getAbsolutePath())
                .put("saved_caches_directory", new File(directory, "saved_caches").getAbsolutePath())
                .put("commitlog_sync", "periodic") // TODO: make configurable
                .put("commitlog_sync_period_in_ms", "10000") // TODO: make configurable
                .put("seeds", asList(config.getSeeds()))
                .put("disk_access_mode", "auto")
                .put("storage_port", config.getStoragePort())
                .put("listen_address", InetAddresses.toUriString(nodeInfo.getPublicIp()))
                .put("rpc_address", InetAddresses.toUriString(nodeInfo.getBindIp()))
                .put("rpc_port", config.getRpcPort())
                .put("endpoint_snitch", "org.apache.cassandra.locator.SimpleSnitch") // TODO: make configurable
                .put("request_scheduler", "org.apache.cassandra.scheduler.NoScheduler")
                .put("in_memory_compaction_limit_in_mb", (int) config.getInMemoryCompactionLimit().getValue(DataSize.Unit.MEGABYTE))
                .put("sliced_buffer_size_in_kb", 64)
                .put("thrift_framed_transport_size_in_mb", 3)
                .put("thrift_max_message_length_in_mb", 4)
                .put("column_index_size_in_kb", (int) config.getColumnIndexSize().getValue(DataSize.Unit.KILOBYTE))
                .build();

        File configFile = new File(directory, "config.yaml");

        Files.write(new Yaml().dump(map), configFile, Charsets.UTF_8);
        System.setProperty("cassandra.config", configFile.toURI().toString());

        cassandra = new CassandraDaemon();
        cassandra.init(null);

        thread = new Thread(new Runnable()
        {
            @Override
            public void run()
            {
                cassandra.start();
            }
        });
        thread.setDaemon(true);
    }

    @PostConstruct
    public void start()
    {
        thread.start();
    }

    @PreDestroy
    public void stop()
    {
        thread.interrupt();
        cassandra.stop();
    }
}
