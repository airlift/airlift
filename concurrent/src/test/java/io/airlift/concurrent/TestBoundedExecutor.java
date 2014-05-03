package io.airlift.concurrent;

import com.google.common.util.concurrent.Uninterruptibles;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class TestBoundedExecutor
{
    private ExecutorService executorService;

    @BeforeMethod
    public void setUp()
            throws Exception
    {
        executorService = Executors.newCachedThreadPool();
    }

    @AfterMethod
    public void tearDown()
            throws Exception
    {
        executorService.shutdownNow();
    }

    @Test
    public void testCounter()
            throws Exception
    {
        BoundedExecutor boundedExecutor = new BoundedExecutor(executorService, 1); // Enforce single thread

        int totalTasks = 100_000;
        final AtomicInteger counter = new AtomicInteger();
        final CountDownLatch startLatch = new CountDownLatch(1);
        final CountDownLatch completeLatch = new CountDownLatch(totalTasks);

        for (int i = 0; i < totalTasks; i++) {
            boundedExecutor.execute(new Runnable()
            {
                @Override
                public void run()
                {
                    try {
                        Uninterruptibles.awaitUninterruptibly(startLatch); // Wait for the go signal

                        // Intentional distinct read and write calls
                        int initialCount = counter.get();
                        counter.set(initialCount + 1);
                    }
                    finally {
                        completeLatch.countDown();
                    }
                }
            });
        }

        startLatch.countDown(); // Signal go for threads
        Uninterruptibles.awaitUninterruptibly(completeLatch, 1, TimeUnit.MINUTES); // Wait for tasks to complete
        Assert.assertEquals(counter.get(), totalTasks);
    }

    @Test
    public void testSingleThreadBound()
            throws Exception
    {
        testBound(1, 100_000);
    }

    @Test
    public void testDoubleThreadBound()
            throws Exception
    {
        testBound(2, 100_000);
    }

    private void testBound(final int maxThreads, int totalTasks)
    {
        BoundedExecutor boundedExecutor = new BoundedExecutor(executorService, maxThreads);

        final AtomicInteger activeThreadCount = new AtomicInteger();
        final CountDownLatch startLatch = new CountDownLatch(1);
        final CountDownLatch completeLatch = new CountDownLatch(totalTasks);
        final AtomicBoolean failed = new AtomicBoolean();

        for (int i = 0; i < totalTasks; i++) {
            boundedExecutor.execute(new Runnable()
            {
                @Override
                public void run()
                {
                    try {
                        Uninterruptibles.awaitUninterruptibly(startLatch); // Wait for the go signal
                        int count = activeThreadCount.incrementAndGet();
                        if (count < 1 || count > maxThreads) {
                            failed.set(true);
                        }
                        activeThreadCount.decrementAndGet();
                    }
                    finally {
                        completeLatch.countDown();
                    }
                }
            });
        }

        startLatch.countDown(); // Signal go for threads
        Uninterruptibles.awaitUninterruptibly(completeLatch, 1, TimeUnit.MINUTES); // Wait for tasks to complete

        Assert.assertFalse(failed.get());
    }
}
