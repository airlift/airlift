package com.proofpoint.zookeeper;

import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;

public class TestZookeeperUtils
{
    private ZookeeperTestServerInstance instance;

    @BeforeMethod
    public void setup()
            throws Exception
    {
        instance = new ZookeeperTestServerInstance(5234);
    }

    @AfterMethod
    public void teardown()
            throws InterruptedException
    {
        instance.close();
    }

    @Test
    public void testMkdirsRoot()
            throws Exception
    {
        ZookeeperClientConfig config = new ZookeeperClientConfig()
        {
            @Override
            public String getConnectionString()
            {
                return instance.getConnectString();
            }

            @Override
            public int getMaxConnectionLossRetries()
            {
                return 1;
            }

            @Override
            public int getConnectionLossSleepInMs()
            {
                return 1000;
            }

            @Override
            public int getConnectionTimeoutInMs()
            {
                return 10000;
            }

            @Override
            public int getSessionTimeoutInMs()
            {
                return 60000;
            }
        };
        ZookeeperClient client = new ZookeeperClient(new DefaultZookeeperClientCreator(config));
        client.start();

        String path = "/";
        client.mkdirs(path);

        client.closeForShutdown();
    }

    @Test
    public void testMkdirsPath()
            throws Exception
    {
        ZookeeperClientConfig config = new ZookeeperClientConfig()
        {
            @Override
            public String getConnectionString()
            {
                return instance.getConnectString();
            }

            @Override
            public int getMaxConnectionLossRetries()
            {
                return 1;
            }

            @Override
            public int getConnectionLossSleepInMs()
            {
                return 1000;
            }

            @Override
            public int getConnectionTimeoutInMs()
            {
                return 10000;
            }

            @Override
            public int getSessionTimeoutInMs()
            {
                return 60000;
            }
        };
        ZookeeperClient client = new ZookeeperClient(new DefaultZookeeperClientCreator(config));
        client.start();

        String path = "/a/b/c";
        client.mkdirs(path);
        assertNotNull(client.exists(path));

        client.closeForShutdown();
    }

    @Test
    public void testMkdirsSinglePath()
            throws Exception
    {
        ZookeeperClientConfig config = new ZookeeperClientConfig()
        {
            @Override
            public String getConnectionString()
            {
                return instance.getConnectString();
            }

            @Override
            public int getMaxConnectionLossRetries()
            {
                return 1;
            }

            @Override
            public int getConnectionLossSleepInMs()
            {
                return 1000;
            }

            @Override
            public int getConnectionTimeoutInMs()
            {
                return 10000;
            }

            @Override
            public int getSessionTimeoutInMs()
            {
                return 60000;
            }
        };
        ZookeeperClient client = new ZookeeperClient(new DefaultZookeeperClientCreator(config));
        client.start();

        String path = "/hello";
        client.mkdirs(path);
        assertNotNull(client.exists(path));

        client.closeForShutdown();
    }

    @Test
    public void testFailsWithNonAbsolutePath()
            throws Exception
    {
        ZookeeperClientConfig config = new ZookeeperClientConfig()
        {
            @Override
            public String getConnectionString()
            {
                return instance.getConnectString();
            }

            @Override
            public int getMaxConnectionLossRetries()
            {
                return 1;
            }

            @Override
            public int getConnectionLossSleepInMs()
            {
                return 1000;
            }

            @Override
            public int getConnectionTimeoutInMs()
            {
                return 10000;
            }

            @Override
            public int getSessionTimeoutInMs()
            {
                return 60000;
            }
        };
        ZookeeperClient client = new ZookeeperClient(new DefaultZookeeperClientCreator(config));
        client.start();

        String path = "hello";
        try {
            client.mkdirs(path);
            Assert.fail();
        }
        catch (IllegalArgumentException e) {
            // success
        }

        assertNull(client.exists("/" + path)); // make sure it didn't create a path rooted at /

        client.closeForShutdown();
    }

    @Test
    public void testFailsWithDoubleSlash()
            throws Exception
    {
        ZookeeperClientConfig config = new ZookeeperClientConfig()
        {
            @Override
            public String getConnectionString()
            {
                return instance.getConnectString();
            }

            @Override
            public int getMaxConnectionLossRetries()
            {
                return 1;
            }

            @Override
            public int getConnectionLossSleepInMs()
            {
                return 1000;
            }

            @Override
            public int getConnectionTimeoutInMs()
            {
                return 10000;
            }

            @Override
            public int getSessionTimeoutInMs()
            {
                return 60000;
            }
        };
        ZookeeperClient client = new ZookeeperClient(new DefaultZookeeperClientCreator(config));
        client.start();

        String path = "/a//b";
        try {
            client.mkdirs(path);
            Assert.fail();
        }
        catch (IllegalArgumentException e) {
            // success
        }

        assertNull(client.exists("/a/b")); // make sure it didn't create a path in error

        client.closeForShutdown();
    }
}
