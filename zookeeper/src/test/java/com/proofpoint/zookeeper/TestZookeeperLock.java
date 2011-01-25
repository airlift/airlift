package com.proofpoint.zookeeper;

import com.proofpoint.crossprocess.CrossProcessLock;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class TestZookeeperLock
{
    private ZookeeperTestServerInstance instance;

    @BeforeMethod
    public void setup()
            throws Exception
    {
        instance = new ZookeeperTestServerInstance();
    }

    @AfterMethod
    public void teardown()
            throws InterruptedException
    {
        instance.close();
    }

    @Test
    public void testTimedLock()
            throws Exception
    {
        String lockPath = "/timed/lock";
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
        ZookeeperClient client1 = new ZookeeperClient(new DefaultZookeeperClientCreator(config));
        ZookeeperClient client2 = new ZookeeperClient(new DefaultZookeeperClientCreator(config));
        client1.start();
        client2.start();

        final CrossProcessLock lock1 = client1.newLock(lockPath);
        final CrossProcessLock lock2 = client2.newLock(lockPath);

        Assert.assertTrue(lock1.tryLock());
        Thread t = new Thread()
        {
            @Override
            public void run()
            {
                try {
                    Thread.sleep(2000);
                    lock1.unlock();
                }
                catch (InterruptedException e) {
                    interrupt();
                }
            }
        };
        t.start();
        Assert.assertFalse(lock2.tryLock());
        Assert.assertTrue(lock2.tryLock(1, TimeUnit.MINUTES));
        Assert.assertFalse(lock1.tryLock());
        lock2.unlock();

        client1.closeForShutdown();
        client2.closeForShutdown();
    }

    @Test
    public void test2Clients()
            throws Exception
    {
        String lockPath = "/a/b";
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
        ZookeeperClient client1 = new ZookeeperClient(new DefaultZookeeperClientCreator(config));
        ZookeeperClient client2 = new ZookeeperClient(new DefaultZookeeperClientCreator(config));
        client1.start();
        client2.start();

        CrossProcessLock lock1 = client1.newLock(lockPath);
        CrossProcessLock lock2 = client2.newLock(lockPath);

        Assert.assertTrue(lock1.tryLock());
        Assert.assertFalse(lock2.tryLock());
        lock1.unlock();

        client1.closeForShutdown();
        client2.closeForShutdown();
    }

    @Test
    public void test2Threads()
            throws Exception
    {
        final String lockPath = "/one/two/three";
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
        final ZookeeperClient client = new ZookeeperClient(new DefaultZookeeperClientCreator(config));
        client.start();

        final AtomicInteger count = new AtomicInteger();

        ExecutorService executor = Executors.newCachedThreadPool();

        final CyclicBarrier start = new CyclicBarrier(2);
        final int loops = 100;

        Callable<Integer> counter = new Callable<Integer>()
        {
            @Override
            public Integer call()
                    throws Exception
            {
                CrossProcessLock lock = client.newLock(lockPath);

                int result = 0;

                start.await();
                lock.lock();
                try {
                    for (int i = 0; i < loops; ++i) {
                        result = count.incrementAndGet();
                        Thread.sleep(1);
                    }
                }
                finally {
                    lock.unlock();
                }

                return result;
            }
        };

        try {
            Future<Integer> t1 = executor.submit(counter);
            Future<Integer> t2 = executor.submit(counter);

            int count1 = t1.get();
            int count2 = t2.get();

            assertEquals(Math.min(count1, count2), loops);
            assertEquals(Math.max(count1, count2), 2 * loops);
        }
        finally {
            executor.shutdownNow();
        }

        client.closeForShutdown();
    }

    @Test
    public void testTryLockFailed()
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
        ZookeeperClient client1 = new ZookeeperClient(new DefaultZookeeperClientCreator(config));
        ZookeeperClient client2 = new ZookeeperClient(new DefaultZookeeperClientCreator(config));
        client1.start();
        client2.start();

        String path = "/lock";
        CrossProcessLock lock1 = client1.newLock(path);
        CrossProcessLock lock2 = client2.newLock(path);

        assertTrue(lock1.tryLock());
        assertFalse(lock2.tryLock());
        lock1.unlock();
        assertTrue(lock1.tryLock());

        client1.closeForShutdown();
        client2.closeForShutdown();
    }

    @Test
    public void testLockUnlockSequence()
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

        String path = "/lock";
        CrossProcessLock lock = client.newLock(path);

        lock.lock();
        lock.unlock();
        assertTrue(lock.tryLock());

        client.closeForShutdown();
    }

    @Test
    public void testTryLockUnlockSequence()
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

        String path = "/lock";
        CrossProcessLock lock = client.newLock(path);

        assertTrue(lock.tryLock());
        lock.unlock();
        assertTrue(lock.tryLock());

        client.closeForShutdown();
    }

}
