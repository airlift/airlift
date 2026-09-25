package io.airlift.concurrent;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static com.google.common.util.concurrent.MoreExecutors.directExecutor;
import static com.google.common.util.concurrent.Uninterruptibles.awaitUninterruptibly;
import static io.airlift.concurrent.Threads.virtualThreadsNamed;
import static java.util.concurrent.Executors.newThreadPerTaskExecutor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

@TestInstance(PER_CLASS)
public class TestBoundedExecutor
{
    private ExecutorService executorService;

    @BeforeAll
    public void setUp()
    {
        executorService = newThreadPerTaskExecutor(virtualThreadsNamed("TestBoundedExecutor-%s"));
    }

    @AfterAll
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

    /**
     * A task canceled while running is guaranteed to leave its runner thread's interrupted status set.
     * Ensure the leftover interrupt is not visible to the next processed task.
     */
    @Test
    public void testInterruptFromCancelledTaskDoesNotLeakToNextTask()
    {
        try (ExecutorService singleThread = Executors.newSingleThreadExecutor()) {
            BoundedExecutor boundedExecutor = new BoundedExecutor(singleThread, 1);

            CountDownLatch started = new CountDownLatch(1);
            AtomicBoolean released = new AtomicBoolean();
            AtomicBoolean interruptedAtStartOfNextTask = new AtomicBoolean(true);
            CountDownLatch nextTaskRan = new CountDownLatch(1);

            Runnable nextTask = () -> {
                interruptedAtStartOfNextTask.set(Thread.currentThread().isInterrupted());
                nextTaskRan.countDown();
            };

            FutureTask<Void> canceledTask = new FutureTask<>(() -> {
                started.countDown();
                // Busy wait rather than an interruptible blocking call to not interfere with interrupt status
                while (!released.get()) {
                    Thread.onSpinWait();
                }
                // Force boundedExecutor to execute both tasks on the same thread
                boundedExecutor.execute(nextTask);
                return null;
            });

            boundedExecutor.execute(canceledTask);
            assertThat(awaitUninterruptibly(started, 1, TimeUnit.MINUTES)).isTrue();

            canceledTask.cancel(true);
            released.set(true);

            assertThat(awaitUninterruptibly(nextTaskRan, 1, TimeUnit.MINUTES)).isTrue();
            assertThat(canceledTask.isCancelled()).isTrue();
            assertThat(interruptedAtStartOfNextTask.get()).isFalse();
        }
    }

    /**
     * Ensure that the initial interrupt state is preserved after executing tasks.
     */
    @Test
    public void testInitialInterruptStatePreserved()
    {
        BoundedExecutor boundedExecutor = new BoundedExecutor(directExecutor(), 1);
        Thread.currentThread().interrupt();
        try {
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
            AtomicBoolean interruptedDuringTask = new AtomicBoolean(true);
            boundedExecutor.execute(() -> interruptedDuringTask.set(Thread.currentThread().isInterrupted()));
            assertThat(interruptedDuringTask.get()).isFalse();
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        }
        finally {
            // clear interrupt status in case the assertion above failed
            Thread.interrupted();
        }
    }

    /**
     * Validates the divergence from MoreExecutors#newSequentialExecutor documented on {@link BoundedExecutor}
     * An interrupt that arises from within a task partway through a drained batch is cleared and not propagated back to the
     * caller once the batch completes. newSequentialExecutor would restore such an interrupt.
     */
    @Test
    public void testInterruptDuringBatchIsNotPropagatedToCaller()
    {
        BoundedExecutor boundedExecutor = new BoundedExecutor(directExecutor(), 1);

        boundedExecutor.execute(() -> {
            Thread.currentThread().interrupt();
            // Force both tasks into the same drainQueue() batch via reentrant submission.
            boundedExecutor.execute(() -> {});
        });

        assertThat(Thread.currentThread().isInterrupted()).isFalse();
    }

    @Test
    public void testReentrantSubmissionWithDirectExecutor()
    {
        BoundedExecutor boundedExecutor = new BoundedExecutor(directExecutor(), 1);

        List<Integer> order = new ArrayList<>();
        boundedExecutor.execute(() -> {
            order.add(1);
            boundedExecutor.execute(() -> order.add(2));
            order.add(3);
        });

        assertThat(order).containsExactly(1, 3, 2);
    }

    /**
     * An {@link Error} thrown by a task is non-recoverable and must propagate rather than being
     * swallowed like an {@link Exception}. With a {@code directExecutor} the task runs synchronously
     * inside {@link BoundedExecutor#execute}, so the Error surfaces directly to the caller.
     */
    @Test
    public void testErrorFromTaskPropagates()
    {
        BoundedExecutor boundedExecutor = new BoundedExecutor(directExecutor(), 1);

        assertThatThrownBy(() -> boundedExecutor.execute(() -> {
            throw new StackOverflowError("boom");
        }))
                .isInstanceOf(StackOverflowError.class)
                .hasMessage("boom");
    }

    /**
     * When a task throws an {@link Error}, the draining thread terminates, but any remaining queued
     * tasks must still be drained (by a re-dispatched draining task) rather than stranded.
     */
    @Test
    public void testRemainingTasksStillRunAfterTaskError()
    {
        try (ExecutorService singleThread = Executors.newSingleThreadExecutor()) {
            BoundedExecutor boundedExecutor = new BoundedExecutor(singleThread, 1);

            CountDownLatch firstStarted = new CountDownLatch(1);
            AtomicBoolean release = new AtomicBoolean();
            CountDownLatch secondRan = new CountDownLatch(1);

            boundedExecutor.execute(() -> {
                firstStarted.countDown();
                // Busy wait until the second task is enqueued into the same batch, then fail hard.
                while (!release.get()) {
                    Thread.onSpinWait();
                }
                throw new StackOverflowError("boom");
            });

            assertThat(awaitUninterruptibly(firstStarted, 1, TimeUnit.MINUTES)).isTrue();
            boundedExecutor.execute(secondRan::countDown);
            release.set(true);

            assertThat(awaitUninterruptibly(secondRan, 1, TimeUnit.MINUTES)).isTrue();
        }
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
