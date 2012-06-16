package io.airlift.cassandra.testing;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Files;
import io.airlift.cassandra.CassandraServerConfig;
import io.airlift.cassandra.CassandraServerInfo;
import io.airlift.cassandra.EmbeddedCassandraServer;
import io.airlift.node.NodeInfo;
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
                    .setPartitioner(CassandraServerConfig.Partitioner.BYTE_ORDERED)
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

            deleteRecursively(tempDir);
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

    //Copied from airlift.testing.FileUtils
    private static boolean deleteDirectoryContents(File directory)
    {
        Preconditions.checkArgument(directory.isDirectory(), "Not a directory: %s", directory);

        // Don't delete symbolic link directories
        if (isSymbolicLink(directory)) {
            return false;
        }

        boolean success = true;
        for (File file : listFiles(directory)) {
            success = deleteRecursively(file) && success;
        }
        return success;
    }

    //Copied from airlift.testing.FileUtils
    private static boolean deleteRecursively(File file)
    {
        boolean success = true;
        if (file.isDirectory()) {
            success = deleteDirectoryContents(file);
        }

        return file.delete() && success;
    }

    //Copied from airlift.testing.FileUtils
    private static boolean isSymbolicLink(File file)
    {
        try {
            File canonicalFile = file.getCanonicalFile();
            File absoluteFile = file.getAbsoluteFile();
            // a symbolic link has a different name between the canonical and absolute path
            return !canonicalFile.getName().equals(absoluteFile.getName()) ||
                    // or the canonical parent path is not the same as the files parent path
                    !canonicalFile.getParent().equals(absoluteFile.getParentFile().getCanonicalPath());
        }
        catch (IOException e) {
            // error on the side of caution
            return true;
        }
    }

    //Copied from airlift.testing.FileUtils
    private static ImmutableList<File> listFiles(File dir)
    {
        File[] files = dir.listFiles();
        if (files == null) {
            return ImmutableList.of();
        }
        return ImmutableList.copyOf(files);
    }
}
