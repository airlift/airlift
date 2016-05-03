package io.airlift.concurrent;

import com.google.common.util.concurrent.Uninterruptibles;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
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

    @BeforeClass
    public void setUp()
            throws Exception
    {
        executorService = Executors.newCachedThreadPool();
    }

    @AfterClass(alwaysRun = true)
    public void tearDown()
            throws Exception
    {
        executorService.shutdownNow();
    }

    @Test
    public void testCounter()
            throws Exception
    {
        int maxThreads = 1;
        BoundedExecutor boundedExecutor = new BoundedExecutor(executorService, maxThreads); // Enforce single thread

        int stageTasks = 100_000;
        int totalTasks = stageTasks * 2;
        AtomicInteger counter = new AtomicInteger();
        CountDownLatch initializeLatch = new CountDownLatch(maxThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completeLatch = new CountDownLatch(totalTasks);

        // Pre-loaded tasks
        for (int i = 0; i < stageTasks; i++) {
            boundedExecutor.execute(() -> {
                try {
                    initializeLatch.countDown();
                    Uninterruptibles.awaitUninterruptibly(startLatch); // Wait for the go signal

                    // Intentional distinct read and write calls
                    int initialCount = counter.get();
                    counter.set(initialCount + 1);
                }
                finally {
                    completeLatch.countDown();
                }
            });
        }

        Uninterruptibles.awaitUninterruptibly(initializeLatch, 1, TimeUnit.MINUTES); // Wait for pre-load tasks to initialize
        startLatch.countDown(); // Signal go for stage1 threads

        // Concurrently submitted tasks
        for (int i = 0; i < stageTasks; i++) {
            boundedExecutor.execute(() -> {
                try {
                    // Intentional distinct read and write calls
                    int initialCount = counter.get();
                    counter.set(initialCount + 1);
                }
                finally {
                    completeLatch.countDown();
                }
            });
        }

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

    @Test
    public void testTripleThreadBound()
            throws Exception
    {
        testBound(3, 100_000);
    }

    private void testBound(final int maxThreads, int stageTasks)
    {
        BoundedExecutor boundedExecutor = new BoundedExecutor(executorService, maxThreads);

        int totalTasks = stageTasks * 2;
        AtomicInteger activeThreadCount = new AtomicInteger();
        CountDownLatch initializeLatch = new CountDownLatch(maxThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completeLatch = new CountDownLatch(totalTasks);
        AtomicBoolean failed = new AtomicBoolean();

        // Pre-loaded tasks
        for (int i = 0; i < stageTasks; i++) {
            boundedExecutor.execute(() -> {
                try {
                    initializeLatch.countDown();
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
            });
        }

        Uninterruptibles.awaitUninterruptibly(initializeLatch, 1, TimeUnit.MINUTES); // Wait for pre-load tasks to initialize
        startLatch.countDown(); // Signal go for stage1 threads

        // Concurrently submitted tasks
        for (int i = 0; i < stageTasks; i++) {
            boundedExecutor.execute(() -> {
                try {
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

        Uninterruptibles.awaitUninterruptibly(completeLatch, 1, TimeUnit.MINUTES); // Wait for tasks to complete

        Assert.assertFalse(failed.get());
    }
}
