package com.proofpoint.zookeeper;

import com.google.common.base.Function;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.MoreExecutors;
import com.proofpoint.configuration.ConfigurationFactory;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
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
    public void testView() throws Exception
    {
        client.mkdirs("/view");

        NodeView        nodeView = new NodeView(client, "/view");
        try
        {
            nodeView.start();

            NodeView.View   view1 = nodeView.getView();
            client.create("/view/test", "one".getBytes());
            nodeView.waitForUpdate(view1.version());
            NodeView.View   view2 = nodeView.getView();
            client.setData("/view/test", "two".getBytes());
            nodeView.waitForUpdate(view2.version());
            NodeView.View   view3 = nodeView.getView();

            Assert.assertEquals(view1.entries().size(), 0);
            Assert.assertEquals(view2.entries().size(), 1);
            Assert.assertEquals(new String(view2.entries().get(0).getValue()), "one");
            Assert.assertEquals(view3.entries().size(), 1);
            Assert.assertEquals(new String(view3.entries().get(0).getValue()), "two");

            client.create("/view/more", "more".getBytes());
            nodeView.waitForUpdate(view3.version());
            NodeView.View   view4 = nodeView.getView();
            Assert.assertEquals(view4.entries().size(), 2);

            Set<String>     view4set = Sets.newHashSet(Iterables.transform(view4.entries(), new Function<Map.Entry<String, byte[]>, String>()
            {
                @Override
                public String apply(Map.Entry<String, byte[]> from)
                {
                    return new String(from.getValue());
                }
            }));
            Assert.assertEquals(view4set, Sets.<Object>newHashSet("two", "more"));

            client.delete("/view/more");
            nodeView.waitForUpdate(view4.version());
            NodeView.View   view5 = nodeView.getView();
            Assert.assertEquals(view5.entries().size(), 1);
            Assert.assertEquals(new String(view5.entries().get(0).getValue()), "two");
        }
        finally
        {
            nodeView.stop();
        }
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
            public void updated(String child, byte[] data)
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
