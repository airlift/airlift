package io.airlift.cassandra;

import com.google.common.base.Preconditions;
import io.airlift.log.Logger;
import org.apache.cassandra.config.CFMetaData;
import org.apache.cassandra.config.ConfigurationException;
import org.apache.cassandra.config.DatabaseDescriptor;
import org.apache.cassandra.db.ColumnFamilyStore;
import org.apache.cassandra.db.SystemTable;
import org.apache.cassandra.db.Table;
import org.apache.cassandra.db.commitlog.CommitLog;
import org.apache.cassandra.db.migration.Migration;
import org.apache.cassandra.gms.Gossiper;
import org.apache.cassandra.service.AbstractCassandraDaemon;
import org.apache.cassandra.service.CassandraDaemon;
import org.apache.cassandra.service.GCInspector;
import org.apache.cassandra.service.MigrationManager;
import org.apache.cassandra.service.StorageService;
import org.apache.cassandra.thrift.Cassandra;
import org.apache.cassandra.thrift.CassandraServer;
import org.apache.cassandra.thrift.CustomTThreadPoolServer;
import org.apache.cassandra.thrift.TBinaryProtocol;
import org.apache.cassandra.thrift.TCustomServerSocket;
import org.apache.cassandra.utils.CLibrary;
import org.apache.thrift.protocol.TProtocolFactory;
import org.apache.thrift.server.TServer;
import org.apache.thrift.server.TThreadPoolServer;
import org.apache.thrift.transport.TFramedTransport;
import org.apache.thrift.transport.TServerSocket;
import org.apache.thrift.transport.TTransportException;
import org.apache.thrift.transport.TTransportFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

public class EmbeddedCassandraDaemon
        implements CassandraDaemon
{
    private final static Logger log = Logger.get(EmbeddedCassandraDaemon.class);
    private int listenPort;
    private InetAddress listenAddr;
    private ThriftServer server;
    private boolean isRunning;

    @Override
    public void init(String[] arguments)
            throws IOException
    {
        try {
            setup();
        }
        catch (ConfigurationException e) {
            throw new IOException("Fatal exception during initialization", e);
        }
    }

    @Override
    public void start()
            throws IOException
    {
        startRPCServer();
    }

    @Override
    public void stop()
    {
        stopRPCServer();
    }

    @Override
    public void destroy()
    {
        throw new AssertionError("destroy method not implemented");
    }

    @Override
    public void startRPCServer()
    {
        synchronized (this) {
            if (!isRunning) {
                log.info("Cassandra starting...");
                server = new ThriftServer(listenAddr, listenPort);
                server.start();

                isRunning = true;
            }
        }
    }

    @Override
    public void stopRPCServer()
    {
        synchronized (this) {
            if (isRunning) {
                log.info("Cassandra shutting down...");
                server.stopServer();
                try {
                    server.join();
                }
                catch (InterruptedException e) {
                    log.error(e, "Interrupted while waiting for thrift server to stop");
                    Thread.currentThread().interrupt();
                }

                isRunning = false;
            }
        }
    }

    @Override
    public boolean isRPCServerRunning()
    {
        synchronized (this) {
            return isRunning;
        }
    }

    @Override
    public void activate()
    {
        throw new AssertionError("activate method not implemented");
    }

    @Override
    public void deactivate()
    {
        throw new AssertionError("deactivate method not implemented");
    }

    private void setup()
            throws IOException, ConfigurationException
    {
        log.info("Heap size: %s/%s", Runtime.getRuntime().totalMemory(), Runtime.getRuntime().maxMemory());
        CLibrary.tryMlockall();

        listenPort = DatabaseDescriptor.getRpcPort();
        listenAddr = DatabaseDescriptor.getRpcAddress();

        Preconditions.checkNotNull(listenPort, "rpc port is null");
        Preconditions.checkNotNull(listenAddr, "rpc address is null");

        // check the system table to keep user from shooting self in foot by changing partitioner, cluster name, etc.
        // we do a one-off scrub of the system table first; we can't load the list of the rest of the tables,
        // until system table is opened.
        for (CFMetaData cfm : DatabaseDescriptor.getTableMetaData(Table.SYSTEM_TABLE).values()) {
            ColumnFamilyStore.scrubDataDirectories(Table.SYSTEM_TABLE, cfm.cfName);
        }
        SystemTable.checkHealth();

        // load keyspace descriptions.
        DatabaseDescriptor.loadSchemas();

        // clean up debris in the rest of the tables
        for (String table : DatabaseDescriptor.getTables()) {
            for (CFMetaData cfm : DatabaseDescriptor.getTableMetaData(table).values()) {
                ColumnFamilyStore.scrubDataDirectories(table, cfm.cfName);
            }
        }

        // initialize keyspaces
        for (String table : DatabaseDescriptor.getTables()) {
            log.debug("opening keyspace " + table);
            Table.open(table);
        }

        try {
            GCInspector.instance.start();
        }
        catch (Throwable t) {
            log.warn("Unable to start GCInspector (currently only supported on the Sun JVM)");
        }

        // replay the log if necessary
        CommitLog.recover();

        // check to see if CL.recovery modified the lastMigrationId. if it did, we need to re apply migrations. this isn't
        // the same as merely reloading the schema (which wouldn't perform file deletion after a DROP). The solution
        // is to read those migrations from disk and apply them.
        UUID currentMigration = DatabaseDescriptor.getDefsVersion();
        UUID lastMigration = Migration.getLastMigrationId();
        if ((lastMigration != null) && (lastMigration.timestamp() > currentMigration.timestamp())) {
            Gossiper.instance.maybeInitializeLocalState(SystemTable.incrementAndGetGeneration());
            MigrationManager.applyMigrations(currentMigration, lastMigration);
        }

        SystemTable.purgeIncompatibleHints();

        // start server internals
        StorageService.instance.registerDaemon(this);
        StorageService.instance.initServer();
    }

    /**
     * Simple class to run the thrift connection accepting code in separate thread of control.
     */
    private static class ThriftServer
            extends Thread
    {
        private final TServer serverEngine;

        public ThriftServer(InetAddress listenAddr, int listenPort)
        {
            setName("ThriftServer");

            // now we start listening for clients
            CassandraServer cassandraServer = new CassandraServer();
            Cassandra.Processor processor = new Cassandra.Processor(cassandraServer);

            // Transport
            TServerSocket tServerSocket;
            String socketName = String.format("%s:%s", listenAddr.getHostAddress(), listenPort);
            try {
                log.info("Binding thrift service to " + socketName);
                tServerSocket = new TCustomServerSocket(
                        new InetSocketAddress(listenAddr, listenPort),
                        DatabaseDescriptor.getRpcKeepAlive(),
                        DatabaseDescriptor.getRpcSendBufferSize(),
                        DatabaseDescriptor.getRpcRecvBufferSize());
            }
            catch (TTransportException e) {
                throw new RuntimeException("Unable to create thrift socket to " + socketName, e);
            }

            // Protocol factory
            TProtocolFactory tProtocolFactory = new TBinaryProtocol.Factory(
                    true, true, DatabaseDescriptor.getThriftMaxMessageLength());

            // Transport factory
            int tFramedTransportSize = DatabaseDescriptor.getThriftFramedTransportSize();
            TTransportFactory inTransportFactory = new TFramedTransport.Factory(tFramedTransportSize);
            TTransportFactory outTransportFactory = new TFramedTransport.Factory(tFramedTransportSize);
            log.info("Using TFastFramedTransport with a max frame size of %s bytes", tFramedTransportSize);

            // ThreadPool Server
            TThreadPoolServer.Args args = new TThreadPoolServer.Args(tServerSocket)
                    .minWorkerThreads(DatabaseDescriptor.getRpcMinThreads())
                    .maxWorkerThreads(DatabaseDescriptor.getRpcMaxThreads())
                    .inputTransportFactory(inTransportFactory)
                    .outputTransportFactory(outTransportFactory)
                    .inputProtocolFactory(tProtocolFactory)
                    .outputProtocolFactory(tProtocolFactory)
                    .processor(processor);

            ExecutorService executorService = new AbstractCassandraDaemon.CleaningThreadPool(
                    cassandraServer.clientState, args.minWorkerThreads, args.maxWorkerThreads);
            serverEngine = new CustomTThreadPoolServer(args, executorService);
        }

        @Override
        public void run()
        {
            log.info("Listening for thrift clients...");
            serverEngine.serve();
        }

        public void stopServer()
        {
            log.info("Stopping listening for thrift clients");
            serverEngine.stop();
        }
    }
}
