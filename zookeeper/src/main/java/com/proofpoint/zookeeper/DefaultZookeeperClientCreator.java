package com.proofpoint.zookeeper;

import com.google.inject.Inject;
import org.apache.hadoop.io.retry.RetryPolicies;
import org.apache.hadoop.io.retry.RetryPolicy;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooKeeper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class DefaultZookeeperClientCreator implements ZookeeperClientCreator
{
    private final AtomicReference<CountDownLatch> startupLatchRef = new AtomicReference<CountDownLatch>(new CountDownLatch(1));
    private final ZookeeperClientConfig config;
    private final List<WatchedEvent> events = new ArrayList<WatchedEvent>();
    private final AtomicBoolean connectionSucceeded = new AtomicBoolean(false);
    private final RetryPolicy retryPolicy;

    private Watcher waitingWatcher = null;  // protected by synchronized(events)

    @Inject
    public DefaultZookeeperClientCreator(ZookeeperClientConfig config)
    {
        this.config = config;

        RetryPolicy                                     policy = RetryPolicies.exponentialBackoffRetry(config.getMaxConnectionLossRetries(), config.getConnectionLossSleepInMs(), TimeUnit.MILLISECONDS);
        Map<Class<? extends Exception>, RetryPolicy>    map = new HashMap<Class<? extends Exception>, RetryPolicy>();
        map.put(KeeperException.ConnectionLossException.class, policy);
        retryPolicy = RetryPolicies.retryByException(RetryPolicies.TRY_ONCE_THEN_FAIL, map);
    }

    @Override
    public RetryPolicy getRetryPolicy()
    {
        return retryPolicy;
    }

    @Override
    public ZooKeeper create() throws Exception
    {
        return new ZooKeeper(config.getConnectionString(), config.getSessionTimeoutInMs(), newWatcher());
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
                        if ( event.getState() == Event.KeeperState.Expired )
                        {
                            connectionSucceeded.set(false);
                        }
                        else if ( event.getState() == Event.KeeperState.SyncConnected )
                        {
                            connectionSucceeded.set(true);
                        }
                        latch.countDown();
                    }
                }

                synchronized(events)
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
    public ConnectionStatus waitForStart(ZooKeeper client, Watcher watcher) throws InterruptedException
    {
        CountDownLatch      latch = startupLatchRef.get();
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

        boolean success = connectionSucceeded.get();
        if ( success )
        {
            synchronized(events)
            {
                waitingWatcher = watcher;
                
                for ( WatchedEvent event : events )
                {
                    watcher.process(event);
                }
                events.clear();
                client.register(watcher);
            }
        }
        return success ? ConnectionStatus.SUCCESS : ConnectionStatus.FAILED;
    }
}
