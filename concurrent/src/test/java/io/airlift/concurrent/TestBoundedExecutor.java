package io.airlift.concurrent;

import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static com.google.common.util.concurrent.Uninterruptibles.awaitUninterruptibly;
import static java.util.concurrent.Executors.newCachedThreadPool;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;

public class TestBoundedExecutor
{
    private ExecutorService executorService;

    @BeforeClass
    public void setUp()
    {
        executorService = newCachedThreadPool();
    }

    @AfterClass(alwaysRun = true)
    public void tearDown()
    {
        executorService.shutdownNow();
    }

    @Test
    public void testCounter()
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

        assertThat(awaitUninterruptibly(initializeLatch, 1, TimeUnit.MINUTES)).isTrue(); // Wait for pre-load tasks to initialize
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

        assertThat(awaitUninterruptibly(completeLatch, 1, TimeUnit.MINUTES)).isTrue(); // Wait for tasks to complete
        assertThat(counter.get()).isEqualTo(totalTasks);
    }

    @Test
    public void testSingleThreadBound()
    {
        testBound(1, 100_000);
    }

    @Test
    public void testDoubleThreadBound()
    {
        testBound(2, 100_000);
    }

    @Test
    public void testTripleThreadBound()
    {
        testBound(3, 100_000);
    }

    @Test
    public void testExecutorCorruptionDetection()
    {
        AtomicBoolean reject = new AtomicBoolean();
        Executor executor = command -> {
            if (reject.get()) {
                throw new RejectedExecutionException("Reject for testing");
            }
            executorService.execute(command);
        };
        BoundedExecutor boundedExecutor = new BoundedExecutor(executor, 1); // Enforce single thread

        // Force the underlying executor to fail
        reject.set(true);
        assertThatThrownBy(() -> boundedExecutor.execute(() -> fail("Should not be run")))
                .isInstanceOf(RejectedExecutionException.class)
                .hasMessage("Reject for testing");

        // Recover the underlying executor, but all new tasks should fail
        reject.set(false);
        assertThatThrownBy(() -> boundedExecutor.execute(() -> fail("Should not be run")))
                .isInstanceOf(RejectedExecutionException.class)
                .hasMessage("BoundedExecutor is in a failed state");
    }

    @SuppressWarnings("SameParameterValue")
    private void testBound(int maxThreads, int stageTasks)
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

        assertThat(awaitUninterruptibly(initializeLatch, 1, TimeUnit.MINUTES)).isTrue(); // Wait for pre-load tasks to initialize
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

        assertThat(awaitUninterruptibly(completeLatch, 1, TimeUnit.MINUTES)).isTrue(); // Wait for tasks to complete

        assertThat(failed.get()).isFalse();
    }
}
