package com.proofpoint.zookeeper;

import com.google.common.base.Predicate;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.proofpoint.concurrent.events.EventQueue;
import com.proofpoint.configuration.ConfigurationFactory;
import org.apache.hadoop.io.retry.RetryPolicy;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.data.Stat;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.mockito.Mockito.mock;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotSame;
import static org.testng.Assert.fail;

public class TestZookeeperClient
{
//    @Test TODO - this doesn't work due to this bug: https://issues.apache.org/jira/browse/ZOOKEEPER-832
    public void     testMultiServer() throws Exception
    {
        ZookeeperTestServerInstance     testServer1 = new ZookeeperTestServerInstance(10000);
        ZookeeperTestServerInstance     testServer2 = new ZookeeperTestServerInstance(10001);
        String                          connectionString = testServer1.getConnectString() + "\\," + testServer2.getConnectString();

        Map<String, String>             props = new HashMap<String, String>();
        props.put("zookeeper.connection-string", connectionString);
        ConfigurationFactory            factory = new ConfigurationFactory(props);
        ZookeeperClient                 client = new ZookeeperClient(new DefaultZookeeperClientCreator(factory.build(ZookeeperClientConfig.class)));
        client.start();

        for ( int i = 0; i < 2; ++i )
        {
            try
            {
                client.exists("/one/two");
            }
            catch ( Exception e )
            {
                fail("Connection Failed", e);
            }

            if ( i == 0 )
            {
                testServer1.close();
            }
        }

        client.closeForShutdown();

        testServer2.close();
    }

    @Test
    public void     testPostCreationEvents() throws Exception
    {
        HashMap<String, String>         properties = new HashMap<String, String>();
        properties.put("zookeeper.connection-string", "foo");
        ConfigurationFactory            configurationFactory = new ConfigurationFactory(properties);

        final ZooKeeper                 mockedClient = mock(ZooKeeper.class);
        DefaultZookeeperClientCreator   clientCreator = new DefaultZookeeperClientCreator(configurationFactory.build(ZookeeperClientConfig.class));
        final Watcher                   watcher = clientCreator.newWatcher();
        final WatchedEvent              connectEvent = new WatchedEvent(Watcher.Event.EventType.None, Watcher.Event.KeeperState.SyncConnected, "/");
        final WatchedEvent              nodeEvent = new WatchedEvent(Watcher.Event.EventType.NodeCreated, Watcher.Event.KeeperState.SyncConnected, "/");
        final WatchedEvent              postCreationEvent = new WatchedEvent(Watcher.Event.EventType.NodeDeleted, Watcher.Event.KeeperState.SyncConnected, "/");

        final CountDownLatch            callableStartLatch = new CountDownLatch(1);
        final CountDownLatch            waitingForPostCreationLatch = new CountDownLatch(1);
        final CountDownLatch            postCreationEventProcessedLatch = new CountDownLatch(1);
        Executors.newSingleThreadExecutor().submit
        (
            new Callable<Void>()
            {
                @Override
                public Void call() throws Exception
                {
                    callableStartLatch.await();

                    watcher.process(connectEvent);
                    watcher.process(nodeEvent);

                    waitingForPostCreationLatch.await();
                    watcher.process(postCreationEvent);
                    postCreationEventProcessedLatch.countDown();

                    return null;
                }
            }
        );

        final List<WatchedEvent>        processedEvents = Lists.newArrayList();
        Watcher                         newWatcher = new Watcher()
        {
            @Override
            public void process(WatchedEvent event)
            {
                processedEvents.add(event);
            }
        };

        callableStartLatch.countDown();
        assertEquals(clientCreator.waitForStart(mockedClient, newWatcher), ZookeeperClientCreator.ConnectionStatus.SUCCESS);

        waitingForPostCreationLatch.countDown();
        postCreationEventProcessedLatch.await();
        
        assertEquals(Arrays.asList(connectEvent, nodeEvent, postCreationEvent), processedEvents);
    }

    @Test
    public void     testCreationEvents() throws Exception
    {
        ZooKeeper                       mockedClient = mock(ZooKeeper.class);
        DefaultZookeeperClientCreator   clientCreator = new DefaultZookeeperClientCreator(mock(ZookeeperClientConfig.class));
        Watcher                         watcher = clientCreator.newWatcher();
        WatchedEvent                    connectEvent = new WatchedEvent(Watcher.Event.EventType.None, Watcher.Event.KeeperState.SyncConnected, "/");
        WatchedEvent                    nodeEvent = new WatchedEvent(Watcher.Event.EventType.NodeCreated, Watcher.Event.KeeperState.SyncConnected, "/");
        watcher.process(connectEvent);
        watcher.process(nodeEvent);

        final List<WatchedEvent>        processedEvents = Lists.newArrayList();
        Watcher                         newWatcher = new Watcher()
        {
            @Override
            public void process(WatchedEvent event)
            {
                processedEvents.add(event);
            }
        };

        assertEquals(clientCreator.waitForStart(mockedClient, newWatcher), ZookeeperClientCreator.ConnectionStatus.SUCCESS);
        assertEquals(Arrays.asList(connectEvent, nodeEvent), processedEvents);
    }

    @Test
    public void     testRetries() throws Exception
    {
        ZookeeperTestServerInstance     testServer = new ZookeeperTestServerInstance();

        final AtomicInteger             retryCount = new AtomicInteger(0);
        Map<String, String>             props = Maps.newHashMap();
        props.put("zookeeper.connection-string", testServer.getConnectString());
        ConfigurationFactory            factory = new ConfigurationFactory(props);
        DefaultZookeeperClientCreator   clientCreator = new DefaultZookeeperClientCreator(factory.build(ZookeeperClientConfig.class))
        {
            @Override
            public RetryPolicy getRetryPolicy()
            {
                return new RetryPolicy()
                {
                    @Override
                    public boolean shouldRetry(Exception e, int retries) throws Exception
                    {
                        retryCount.incrementAndGet();
                        return retries < 1;
                    }
                };
            }
        };
        ZookeeperClient                 client = new ZookeeperClient(clientCreator);
        client.start();
        client.exists("/"); // blocks until connection is achieved

        testServer.close(); // next client call should fail

        try
        {
            client.exists("/");
            fail();
        }
        catch ( Exception e )
        {
            // correct
        }
        assertEquals(retryCount.get(), 2);

        // now try with a background call

        final CountDownLatch        latch = new CountDownLatch(1);
        client.setErrorHandler(new ZookeeperClientErrorHandler()
        {
            @Override
            public void connectionLost(ZookeeperClient client)
            {
                latch.countDown();
            }
        });
        retryCount.set(0);
        client.inBackground("").exists("/");
        Assert.assertTrue(latch.await(1, TimeUnit.MINUTES));
        assertEquals(retryCount.get(), 2);

        client.closeForShutdown();
    }

    @Test
    public void     testConnection() throws Exception
    {
        ZookeeperTestServerInstance     testServer = new ZookeeperTestServerInstance(4532);

        Map<String, String>             props = Maps.newHashMap();
        props.put("zookeeper.connection-string", testServer.getConnectString());
        ConfigurationFactory            factory = new ConfigurationFactory(props);
        ZookeeperClient                 client = new ZookeeperClient(new DefaultZookeeperClientCreator(factory.build(ZookeeperClientConfig.class)));
        client.start();

        try
        {
            client.exists("/one/two");
        }
        catch ( Exception e )
        {
            fail("Connection Failed", e);
        }

        client.closeForShutdown();

        testServer.close();
    }

    @Test
    public void    testMessaging() throws Exception
    {
        ZookeeperTestServerInstance     testServer = new ZookeeperTestServerInstance(4532);

        Map<String, String>             props = Maps.newHashMap();
        props.put("zookeeper.connection-string", testServer.getConnectString());
        ConfigurationFactory            factory = new ConfigurationFactory(props);
        ZookeeperClient                 client = new ZookeeperClient(new DefaultZookeeperClientCreator(factory.build(ZookeeperClientConfig.class)));

        final String                    path = "/one";
        final CountDownLatch            latch = new CountDownLatch(1);
        client.addListener
        (
            new EventQueue.EventListener<com.proofpoint.zookeeper.ZookeeperEvent>()
            {
                @Override
                public void eventProcessed(ZookeeperEvent event) throws Exception
                {
                    if ( event.getType() == ZookeeperEvent.Type.CREATE )
                    {
                        assertEquals(event.getPath(), path);
                        latch.countDown();
                    }
                }
           },
           new Predicate<ZookeeperEvent>()
           {
               @Override
               public boolean apply(ZookeeperEvent event)
               {
                   return isOurEvent(event, path);
               }
           }
        );

        client.start();

        client.inBackground(null).create(path, new byte[0]);

        latch.await(30, TimeUnit.SECONDS);
        assertEquals(latch.getCount(), 0);

        client.closeForShutdown();

        testServer.close();
    }

    @Test
    public void    testMessagingMultiClient() throws Exception
    {
        ZookeeperTestServerInstance     testServer = new ZookeeperTestServerInstance(4325);

        Map<String, String>             props = Maps.newHashMap();
        props.put("zookeeper.connection-string", testServer.getConnectString());
        ConfigurationFactory            factory = new ConfigurationFactory(props);
        ZookeeperClient                 client1 = new ZookeeperClient(new DefaultZookeeperClientCreator(factory.build(ZookeeperClientConfig.class)));
        ZookeeperClient                 client2 = new ZookeeperClient(new DefaultZookeeperClientCreator(factory.build(ZookeeperClientConfig.class)));

        final String                    path = "/one";
        final CountDownLatch            latch = new CountDownLatch(1);
        client2.addListener
        (
            new EventQueue.EventListener<com.proofpoint.zookeeper.ZookeeperEvent>()
            {
                @Override
                public void eventProcessed(ZookeeperEvent event) throws Exception
                {
                    if ( event.getType() == ZookeeperEvent.Type.EXISTS)
                    {
                        assertEquals(event.getPath(), path);
                        latch.countDown();
                    }
                }
            },
            new Predicate<ZookeeperEvent>()
            {
                @Override
                public boolean apply(ZookeeperEvent event)
                {
                    return isOurEvent(event, path);
                }
            }
        );

        client1.start();
        client2.start();

        client2.inBackground(null).exists(path);
        client1.create(path, new byte[0]);

        latch.await(30, TimeUnit.SECONDS);
        assertEquals(latch.getCount(), 0);

        client1.closeForShutdown();
        client2.closeForShutdown();

        testServer.close();
    }

    @Test
    public void     testVersionMismatch() throws Exception
    {
        ZookeeperTestServerInstance     testServer = new ZookeeperTestServerInstance();

        Map<String, String>             props = Maps.newHashMap();
        props.put("zookeeper.connection-string", testServer.getConnectString());
        ConfigurationFactory            factory = new ConfigurationFactory(props);
        ZookeeperClient                 client = new ZookeeperClient(new DefaultZookeeperClientCreator(factory.build(ZookeeperClientConfig.class)));

        final String                    path = "/one";

        client.start();

        client.create(path, new byte[0]);
        Stat        preChangeStat = client.exists(path);
        assertEquals(preChangeStat.getVersion(), 0);
        client.setData(path, "test".getBytes());
        Stat        postChangeStat = client.exists(path);
        assertNotSame(preChangeStat.getVersion(), postChangeStat.getVersion());
        try
        {
            client.dataVersion(preChangeStat.getVersion()).setData(path, "something".getBytes());
            fail();
        }
        catch ( KeeperException.BadVersionException e )
        {
            // sucess
        }
        catch ( Exception e )
        {
            fail("", e);
        }

        client.closeForShutdown();

        testServer.close();
    }

    private boolean isOurEvent(ZookeeperEvent event, String path)
    {
        if ( event.getPath() != null )
        {
            if ( event.getPath().startsWith(path) )
            {
                return true;
            }
        }

        return false;
    }
}
