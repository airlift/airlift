package com.proofpoint.zookeeper;

import com.google.common.util.concurrent.MoreExecutors;
import com.proofpoint.configuration.ConfigurationFactory;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

public class TestChildrenSync
{
    private ZookeeperClient client;
    private ZookeeperTestServerInstance server;

    @BeforeMethod
    public void setup() throws Exception
    {
        server = new ZookeeperTestServerInstance();
        Map<String, String> props = new HashMap<String, String>();
        props.put("zookeeper.connection-string", server.getConnectString());
        ZookeeperClientConfig config = new ConfigurationFactory(props).build(ZookeeperClientConfig.class);
        client = new ZookeeperClient(new DefaultZookeeperClientCreator(config));
        client.start();
    }

    @AfterMethod
    public void teardown() throws InterruptedException
    {
        client.closeForShutdown();
        server.close();
    }

    @Test
    public void test()
        throws Exception
    {
        client.mkdirs("/test");

        final BlockingQueue<String> event = new ArrayBlockingQueue<String>(1);
        ChildDataWatcher sync = new ChildDataWatcher(client, "/test", new ChildDataListener()
        {
            @Override
            public void added(String child, byte[] data)
            {
                event.offer("add/" + child + "/" + new String(data));
            }

            @Override
            public void updated(String child, byte[] data, int version)
            {
                event.offer("update/" + child + "/" + new String(data));
            }

            @Override
            public void removed(String child)
            {
                event.offer("remove/" + child);
            }
        }, MoreExecutors.sameThreadExecutor());
        sync.start();

        {
            client.create("/test/child", "hey".getBytes());
            String  value = event.poll(1, TimeUnit.MINUTES);
            Assert.assertEquals("add/child/hey", value);
        }

        {
            client.setData("/test/child", "yo".getBytes());
            String  value = event.poll(1, TimeUnit.MINUTES);
            Assert.assertEquals("update/child/yo", value);
        }

        {
            client.delete("/test/child");
            String  value = event.poll(1, TimeUnit.MINUTES);
            Assert.assertEquals("remove/child", value);
        }

        sync.stop();
    }
}
