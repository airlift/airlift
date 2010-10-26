package com.proofpoint.zookeeper;

import com.proofpoint.log.Logger;
import ch.qos.logback.classic.Level;
import com.proofpoint.io.TempLocalDirectory;
import org.apache.zookeeper.ClientCnxn;
import org.apache.zookeeper.server.FinalRequestProcessor;
import org.apache.zookeeper.server.NIOServerCnxn;
import org.apache.zookeeper.server.ZooKeeperServer;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.BindException;
import java.net.InetSocketAddress;

/**
 * manages an internally running ZooKeeper server and clients to that server.
 * FOR TESTING PURPOSES ONLY
 */
public class ZookeeperTestServerInstance
{
    private static final Logger     log = Logger.get(ZookeeperTestServerInstance.class);
    static
    {
        ((ch.qos.logback.classic.Logger) LoggerFactory.getLogger(FinalRequestProcessor.class)).setLevel(Level.ERROR);
        ((ch.qos.logback.classic.Logger) LoggerFactory.getLogger(ClientCnxn.class)).setLevel(Level.ERROR);
        ((ch.qos.logback.classic.Logger) LoggerFactory.getLogger(ZookeeperEvent.class)).setLevel(Level.ERROR);
    }

    private final ZooKeeperServer       server;
    private final int                   port;
    private final NIOServerCnxn.Factory factory;
    private final TempLocalDirectory    tempLocalDirectory;

    private static final int TIME_IN_MS = 2000;

    /**
     * Create the server using a default port
     *
     * @throws Exception errors
     */
    public ZookeeperTestServerInstance() throws Exception
    {
        this(4534);
    }

    /**
     * Create the server using the given port
     *
     * @param port the port
     * @throws Exception errors
     */
    public ZookeeperTestServerInstance(int port)
            throws Exception
    {
        this.port = port;

        tempLocalDirectory = new TempLocalDirectory();
        tempLocalDirectory.cleanupPrevious();
        File tempDirectory = tempLocalDirectory.newDirectory();

        log.info("Test Zookeeper port: %d path: %s", port, tempDirectory);

        File                    logDir = new File(tempDirectory, "log");
        File                    dataDir = new File(tempDirectory, "data");

        try
        {
            server = new ZooKeeperServer(dataDir, logDir, TIME_IN_MS);
            factory = new NIOServerCnxn.Factory(new InetSocketAddress(port));
            factory.startup(server);
        }
        catch ( BindException e )
        {
            log.debug("Address is in use: %d", port);
            throw e;
        }
    }

    /**
     * Close the server and any open clients
     *
     * @throws InterruptedException thread interruption
     */
    public void             close() throws InterruptedException
    {
        try
        {
            server.shutdown();
            factory.shutdown();
        }
        finally
        {
            tempLocalDirectory.cleanup();
        }
    }

    /**
     * Returns the temp directory instance
     *
     * @return temp directory
     */
    public TempLocalDirectory getTempDirectory()
    {
        return tempLocalDirectory;
    }

    /**
     * Returns the connection string to use
     *
     * @return connection string
     */
    public String getConnectString()
    {
        return "localhost:" + port;
    }
}
