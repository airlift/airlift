package com.proofpoint.zookeeper;

import com.google.common.base.Predicate;
import com.google.common.collect.Maps;
import com.proofpoint.concurrent.events.EventQueue;
import com.proofpoint.configuration.ConfigurationFactory;
import org.apache.hadoop.io.retry.RetryPolicy;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

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
                Assert.fail("Connection Failed", e);
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
            Assert.fail();
        }
        catch ( Exception e )
        {
            // correct
        }
        Assert.assertEquals(retryCount.get(), 2);

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
        Assert.assertEquals(retryCount.get(), 2);

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
            Assert.fail("Connection Failed", e);
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
                        Assert.assertEquals(event.getPath(), path);
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

        latch.await(5, TimeUnit.SECONDS);
        Assert.assertEquals(latch.getCount(), 0);

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
                        Assert.assertEquals(event.getPath(), path);
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

        latch.await(5, TimeUnit.SECONDS);
        Assert.assertEquals(latch.getCount(), 0);

        client1.closeForShutdown();
        client2.closeForShutdown();

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
