package io.airlift.concurrent;

import com.google.common.base.Throwables;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static com.google.common.util.concurrent.Uninterruptibles.awaitUninterruptibly;
import static io.airlift.concurrent.BoundedThreadPool.newBoundedThreadPool;
import static io.airlift.concurrent.Threads.threadsNamed;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;

@Test(singleThreaded = true)
public class TestBoundedThreadPool
{
    private ExecutorService executor;

    @AfterMethod(alwaysRun = true)
    public void tearDown()
            throws Exception
    {
        executor.shutdownNow();
    }

    @Test
    public void testCounter()
            throws Exception
    {
        executor = newBoundedThreadPool(1, threadsNamed("test-%s")); // Enforce single thread

        int totalTasks = 100_000;
        AtomicInteger counter = new AtomicInteger();
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completeLatch = new CountDownLatch(totalTasks);

        for (int i = 0; i < totalTasks; i++) {
            executor.execute(() -> {
                try {
                    awaitUninterruptibly(startLatch); // Wait for the go signal

                    // Intentional distinct read and write calls
                    int initialCount = counter.get();
                    counter.set(initialCount + 1);
                }
                finally {
                    completeLatch.countDown();
                }
            });
        }

        startLatch.countDown(); // Signal go for threads
        awaitUninterruptibly(completeLatch, 1, MINUTES); // Wait for tasks to complete
        assertEquals(counter.get(), totalTasks);
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

    private void testBound(int maxThreads, int totalTasks)
    {
        executor = newBoundedThreadPool(maxThreads, threadsNamed("test-%s"));

        AtomicInteger activeThreadCount = new AtomicInteger();
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completeLatch = new CountDownLatch(totalTasks);
        AtomicBoolean failed = new AtomicBoolean();

        for (int i = 0; i < totalTasks; i++) {
            executor.execute(() -> {
                try {
                    awaitUninterruptibly(startLatch); // Wait for the go signal
                    int count = activeThreadCount.incrementAndGet();
                    if (count < 1 || count > maxThreads) {
                        failed.set(true);
                    }
                    activeThreadCount.decrementAndGet();
                }
                finally {
                    completeLatch.countDown();
                }
            });
        }

        startLatch.countDown(); // Signal go for threads
        awaitUninterruptibly(completeLatch, 1, MINUTES); // Wait for tasks to complete

        assertFalse(failed.get());
    }

    @Test(enabled = false)
    public void testThreadCreation()
    {
        int batchSize = 5;
        CountingThreadFactory threadFactory = new CountingThreadFactory();
        executor = newBoundedThreadPool(batchSize * 2, threadFactory);

        for (int batch = 0; batch < 10; batch++) {
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch completeLatch = new CountDownLatch(batchSize);
            AtomicBoolean failed = new AtomicBoolean();

            for (int i = 0; i < batchSize; i++) {
                executor.execute(() -> {
                    try {
                        awaitUninterruptibly(startLatch); // Wait for the go signal
                        if (threadFactory.getCount() > batchSize) {
                            failed.set(true);
                        }
                    }
                    finally {
                        completeLatch.countDown();
                    }
                });
            }

            startLatch.countDown(); // Signal go for threads
            awaitUninterruptibly(completeLatch, 1, MINUTES); // Wait for tasks to complete

            assertFalse(failed.get());
        }
    }

    @Test
    public void testActiveThreads()
            throws Exception
    {
        int tasks = 10;
        executor = newBoundedThreadPool(tasks, threadsNamed("test-%s"));
        CyclicBarrier barrier = new CyclicBarrier(tasks);
        AtomicBoolean failed = new AtomicBoolean();

        for (int i = 0; i < tasks; i++) {
            CountDownLatch started = new CountDownLatch(1);
            executor.execute(() -> {
                started.countDown();
                try {
                    barrier.await(10, SECONDS);
                }
                catch (InterruptedException | BrokenBarrierException | TimeoutException e) {
                    failed.set(true);
                    throw Throwables.propagate(e);
                }
            });
            started.await(10, SECONDS);
        }

        assertFalse(barrier.isBroken());
        assertFalse(failed.get());
    }

    private static class CountingThreadFactory
        implements ThreadFactory
    {
        private final AtomicLong count = new AtomicLong();

        @Override
        public Thread newThread(Runnable runnable)
        {
            return new Thread(runnable, "test-" + count.getAndIncrement());
        }

        public long getCount()
        {
            return count.get();
        }
    }
}
