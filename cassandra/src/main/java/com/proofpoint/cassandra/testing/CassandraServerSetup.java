package com.proofpoint.cassandra.testing;

import com.google.common.base.Preconditions;
import com.google.common.io.Files;
import com.proofpoint.cassandra.CassandraServerConfig;
import com.proofpoint.cassandra.CassandraServerInfo;
import com.proofpoint.cassandra.EmbeddedCassandraServer;
import com.proofpoint.node.NodeInfo;
import org.apache.cassandra.config.ConfigurationException;
import org.apache.thrift.transport.TTransportException;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.concurrent.atomic.AtomicBoolean;

public class CassandraServerSetup
{
    private final static AtomicBoolean initialized = new AtomicBoolean();
    private final static AtomicBoolean shutdown = new AtomicBoolean();

    private static File tempDir;
    private static EmbeddedCassandraServer server;
    private static int rpcPort;
    private static NodeInfo nodeInfo;

    public static void tryInitialize()
            throws IOException, TTransportException, ConfigurationException, InterruptedException
    {
        Preconditions.checkState(!shutdown.get(), "Cassandra has already been shut down");

        if (initialized.compareAndSet(false, true)) {
            rpcPort = findUnusedPort();
            tempDir = Files.createTempDir();
            CassandraServerConfig config = new CassandraServerConfig()
                    .setSeeds("localhost")
                    .setStoragePort(findUnusedPort())
                    .setRpcPort(rpcPort)
                    .setClusterName("megacluster")
                    .setDirectory(tempDir);

            nodeInfo = new NodeInfo("testing");

            server = new EmbeddedCassandraServer(config, nodeInfo);
            server.start();
        }
    }

    public static void tryShutdown()
            throws IOException
    {
        if (shutdown.compareAndSet(false, true)) {
            server.stop();
            try {
                Files.deleteRecursively(tempDir);
            }
            catch (IOException e) {
                // ignore
            }
        }
    }

    public static CassandraServerInfo getServerInfo()
    {
        Preconditions.checkState(initialized.get(), "Embedded Cassandra instance not yet initialized. Make sure to call tryInitialize() before calling this method.");
        return new CassandraServerInfo(nodeInfo.getBindIp(), rpcPort);
    }

    private static int findUnusedPort()
            throws IOException
    {
        ServerSocket socket = new ServerSocket();
        try {
            socket.bind(new InetSocketAddress(0));
            return socket.getLocalPort();
        }
        finally {
            socket.close();
        }
    }
}
