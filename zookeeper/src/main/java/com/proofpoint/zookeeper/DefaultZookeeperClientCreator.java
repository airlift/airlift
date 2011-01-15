package com.proofpoint.zookeeper;

import com.google.inject.Inject;
import com.proofpoint.log.Logger;
import org.apache.commons.io.FileUtils;
import org.apache.hadoop.io.retry.RetryPolicies;
import org.apache.hadoop.io.retry.RetryPolicy;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooKeeper;
import org.codehaus.jackson.map.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class DefaultZookeeperClientCreator
        implements ZookeeperClientCreator
{
    private final Logger log = Logger.get(getClass());

    private final AtomicReference<CountDownLatch> startupLatchRef = new AtomicReference<CountDownLatch>(new CountDownLatch(1));
    private final ZookeeperClientConfig config;
    private final List<WatchedEvent> events = new ArrayList<WatchedEvent>();
    private final AtomicReference<ConnectionStatus> connectionStatus = new AtomicReference<ConnectionStatus>(null);
    private final RetryPolicy retryPolicy;

    private boolean forceUseNewSession = false;
    private Watcher waitingWatcher = null;  // protected by synchronized(events)

    @Inject
    public DefaultZookeeperClientCreator(ZookeeperClientConfig config)
    {
        this.config = config;

        RetryPolicy policy = RetryPolicies.exponentialBackoffRetry(config.getMaxConnectionLossRetries(), config.getConnectionLossSleepInMs(), TimeUnit.MILLISECONDS);
        Map<Class<? extends Exception>, RetryPolicy> map = new HashMap<Class<? extends Exception>, RetryPolicy>();
        map.put(KeeperException.ConnectionLossException.class, policy);
        retryPolicy = RetryPolicies.retryByException(RetryPolicies.TRY_ONCE_THEN_FAIL, map);
    }

    @Override
    public RetryPolicy getRetryPolicy()
    {
        return retryPolicy;
    }

    @Override
    public ZooKeeper recreateWithNewSession()
            throws Exception
    {
        forceUseNewSession = true;
        startupLatchRef.set(new CountDownLatch(1));
        connectionStatus.set(null);
        events.clear();

        return create();
    }

    @Override
    public ZooKeeper create()
            throws Exception
    {
        ZooKeeper       keeper;

        //noinspection LoopStatementThatDoesntLoop
        do {
            ZookeeperSessionID      session = readSessionId();
            if ( session != null ) {
                try {
                    keeper = new ZooKeeper(config.getConnectionString(), config.getSessionTimeoutInMs(), newWatcher(), session.getSessionId(), session.getPassword());
                    break;
                }
                catch ( IOException e ) {
                    log.warn(e, "Could not read/write session file: %s", config.getSessionStorePath());
                }
            }

            keeper = new ZooKeeper(config.getConnectionString(), config.getSessionTimeoutInMs(), newWatcher());
        } while ( false );

        return keeper;
    }

    private String getSessionStorePath()
    {
        return forceUseNewSession ? null : config.getSessionStorePath();
    }

    private ZookeeperSessionID readSessionId()
    {
        if ( getSessionStorePath() != null ) {
            File sessionIdFile = new File(getSessionStorePath());
            if ( sessionIdFile.exists() ) {
                try {
                    String sessionSpec = FileUtils.readFileToString(sessionIdFile);
                    ObjectMapper        mapper = new ObjectMapper();
                    return mapper.readValue(sessionSpec, ZookeeperSessionID.class);
                }
                catch ( IOException e ) {
                    log.warn(e, "Could not read/write session file: %s", getSessionStorePath());
                }
            }
        }

        return null;
    }

    private void writeSessionId(ZooKeeper keeper)
    {
        if ( getSessionStorePath() != null ) {
            ZookeeperSessionID session = new ZookeeperSessionID();
            session.setPassword(keeper.getSessionPasswd());
            session.setSessionId(keeper.getSessionId());
            ObjectMapper mapper = new ObjectMapper();
            try {
                String                  sessionSpec = mapper.writeValueAsString(session);
                FileUtils.writeStringToFile(new File(getSessionStorePath()), sessionSpec);
            }
            catch ( IOException e ) {
                log.warn(e, "Couldn't write session info to: %s", getSessionStorePath());
            }
        }
    }

    /**
     * Made public for testing purposes
     *
     * @return return a watcher for testing
     */
    public Watcher newWatcher()
    {
        return new Watcher()
        {
            @Override
            public void process(WatchedEvent event)
            {
                CountDownLatch latch = startupLatchRef.get();
                if ( latch != null )
                {
                    if ( event.getType() == Event.EventType.None )
                    {
                        if ( event.getState() == Event.KeeperState.SyncConnected )
                        {
                            connectionStatus.set(ConnectionStatus.SUCCESS);
                        }
                        else
                        {
                            connectionStatus.set((event.getState() == Event.KeeperState.Expired) ? ConnectionStatus.INVALID_SESSION : ConnectionStatus.FAILED);
                        }
                        latch.countDown();
                    }
                }

                synchronized (events)
                {
                    if ( waitingWatcher != null )
                    {
                        waitingWatcher.process(event);
                    }
                    else
                    {
                        events.add(event);
                    }
                }
            }
        };
    }

    @Override
    public ConnectionStatus waitForStart(ZooKeeper client, Watcher watcher)
            throws InterruptedException
    {
        CountDownLatch latch = startupLatchRef.get();
        if ( latch != null )
        {
            try
            {
                latch.await(config.getConnectionTimeoutInMs(), TimeUnit.MILLISECONDS);
            }
            finally
            {
                startupLatchRef.set(null);
            }
        }

        ConnectionStatus status = connectionStatus.get();
        if ( status == ConnectionStatus.SUCCESS )
        {
            synchronized (events)
            {
                waitingWatcher = watcher;

                for ( WatchedEvent event : events )
                {
                    watcher.process(event);
                }
                events.clear();
                client.register(watcher);
            }

            writeSessionId(client);
        }
        return status;
    }
}
