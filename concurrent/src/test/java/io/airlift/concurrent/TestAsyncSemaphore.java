/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.airlift.concurrent;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.SettableFuture;
import com.google.common.util.concurrent.Uninterruptibles;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import javax.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.IntStream;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.util.concurrent.Futures.addCallback;
import static com.google.common.util.concurrent.Futures.immediateCancelledFuture;
import static com.google.common.util.concurrent.Futures.immediateFailedFuture;
import static com.google.common.util.concurrent.MoreExecutors.directExecutor;
import static com.google.common.util.concurrent.MoreExecutors.listeningDecorator;
import static io.airlift.concurrent.MoreFutures.asVoid;
import static io.airlift.concurrent.Threads.daemonThreadsNamed;
import static io.airlift.testing.Assertions.assertLessThanOrEqual;
import static java.util.concurrent.Executors.newCachedThreadPool;
import static org.assertj.core.api.Assertions.assertThat;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

public class TestAsyncSemaphore
{
    private final ListeningExecutorService executor = listeningDecorator(newCachedThreadPool(daemonThreadsNamed("async-semaphore-%s")));

    @Test
    public void testInlineExecution()
            throws Exception
    {
        AsyncSemaphore<Runnable, Void> asyncSemaphore = new AsyncSemaphore<>(1, executor, this::submitTask);

        AtomicInteger count = new AtomicInteger();

        List<ListenableFuture<Void>> futures = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            futures.add(asyncSemaphore.submit(count::incrementAndGet));
        }

        // Wait for completion
        Futures.allAsList(futures).get(1, TimeUnit.MINUTES);

        assertEquals(count.get(), 1000);
    }

    @Test
    public void testSingleThreadBoundedConcurrency()
            throws Exception
    {
        AsyncSemaphore<Runnable, Void> asyncSemaphore = new AsyncSemaphore<>(1, executor, this::submitTask);

        AtomicInteger count = new AtomicInteger();
        AtomicInteger concurrency = new AtomicInteger();

        List<ListenableFuture<Void>> futures = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            futures.add(asyncSemaphore.submit((Runnable) () -> {
                count.incrementAndGet();
                int currentConcurrency = concurrency.incrementAndGet();
                assertLessThanOrEqual(currentConcurrency, 1);
                Uninterruptibles.sleepUninterruptibly(1, TimeUnit.MILLISECONDS);
                concurrency.decrementAndGet();
            }));
        }

        // Wait for completion
        Futures.allAsList(futures).get(1, TimeUnit.MINUTES);

        assertEquals(count.get(), 1000);
    }

    @Test
    public void testMultiThreadBoundedConcurrency()
            throws Exception
    {
        AsyncSemaphore<Runnable, Void> asyncSemaphore = new AsyncSemaphore<>(2, executor, this::submitTask);

        AtomicInteger count = new AtomicInteger();
        AtomicInteger concurrency = new AtomicInteger();

        List<ListenableFuture<Void>> futures = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            futures.add(asyncSemaphore.submit(() -> {
                count.incrementAndGet();
                int currentConcurrency = concurrency.incrementAndGet();
                assertLessThanOrEqual(currentConcurrency, 2);
                Uninterruptibles.sleepUninterruptibly(1, TimeUnit.MILLISECONDS);
                concurrency.decrementAndGet();
            }));
        }

        // Wait for completion
        Futures.allAsList(futures).get(1, TimeUnit.MINUTES);

        assertEquals(count.get(), 1000);
    }

    @Test
    public void testMultiSubmitters()
            throws Exception
    {
        AsyncSemaphore<Runnable, Void> asyncSemaphore = new AsyncSemaphore<>(2, executor, this::submitTask);

        AtomicInteger count = new AtomicInteger();
        AtomicInteger concurrency = new AtomicInteger();

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completionLatch = new CountDownLatch(100);
        for (int i = 0; i < 100; i++) {
            executor.execute(() -> {
                Uninterruptibles.awaitUninterruptibly(startLatch, 1, TimeUnit.MINUTES);
                asyncSemaphore.submit((Runnable) () -> {
                    count.incrementAndGet();
                    int currentConcurrency = concurrency.incrementAndGet();
                    assertLessThanOrEqual(currentConcurrency, 2);
                    Uninterruptibles.sleepUninterruptibly(1, TimeUnit.MILLISECONDS);
                    concurrency.decrementAndGet();
                    completionLatch.countDown();
                });
            });
        }
        // Start the submitters;
        startLatch.countDown();

        // Wait for completion
        Uninterruptibles.awaitUninterruptibly(completionLatch, 1, TimeUnit.MINUTES);

        assertEquals(count.get(), 100);
    }

    @Test
    public void testFailedTasks()
            throws Exception
    {
        AsyncSemaphore<Runnable, Void> asyncSemaphore = new AsyncSemaphore<>(2, executor, this::submitTask);

        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failureCount = new AtomicInteger();
        AtomicInteger concurrency = new AtomicInteger();
        CountDownLatch completionLatch = new CountDownLatch(1000);

        List<ListenableFuture<Void>> futures = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            ListenableFuture<Void> future = asyncSemaphore.submit(() -> assertFailedConcurrency(concurrency));
            addCallback(future, completionCallback(successCount, failureCount, completionLatch), directExecutor());
            futures.add(future);
        }

        // Wait for all tasks and callbacks to complete
        completionLatch.await(1, TimeUnit.MINUTES);

        for (ListenableFuture<Void> future : futures) {
            try {
                future.get();
                fail();
            }
            catch (Exception ignored) {
            }
        }

        assertEquals(successCount.get(), 0);
        assertEquals(failureCount.get(), 1000);
    }

    @Test
    public void testFailedTaskSubmission()
            throws Exception
    {
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failureCount = new AtomicInteger();
        AtomicInteger concurrency = new AtomicInteger();
        CountDownLatch completionLatch = new CountDownLatch(1000);
        AsyncSemaphore<Runnable, Void> asyncSemaphore = new AsyncSemaphore<>(2, executor, task -> {
            throw assertFailedConcurrency(concurrency);
        });

        List<ListenableFuture<Void>> futures = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            // Should never execute this future
            ListenableFuture<Void> future = asyncSemaphore.submit(() -> fail(null));
            addCallback(future, completionCallback(successCount, failureCount, completionLatch), directExecutor());
            futures.add(future);
        }

        // Wait for all tasks and callbacks to complete
        completionLatch.await(1, TimeUnit.MINUTES);

        for (ListenableFuture<Void> future : futures) {
            try {
                future.get();
                fail();
            }
            catch (Exception ignored) {
            }
        }

        assertEquals(successCount.get(), 0);
        assertEquals(failureCount.get(), 1000);
    }

    @Test
    public void testFailedTaskWithMultipleSubmitters()
            throws Exception
    {
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failureCount = new AtomicInteger();
        AtomicInteger concurrency = new AtomicInteger();
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completionLatch = new CountDownLatch(100);

        AsyncSemaphore<Runnable, Void> asyncSemaphore = new AsyncSemaphore<>(2, executor, task -> {
            throw assertFailedConcurrency(concurrency);
        });

        Queue<ListenableFuture<Void>> futures = new ConcurrentLinkedQueue<>();
        for (int i = 0; i < 100; i++) {
            executor.execute(() -> {
                Uninterruptibles.awaitUninterruptibly(startLatch, 1, TimeUnit.MINUTES);
                // Should never execute this future
                ListenableFuture<Void> future = asyncSemaphore.submit(() -> fail(null));
                futures.add(future);
                addCallback(future, completionCallback(successCount, failureCount, completionLatch), directExecutor());
            });
        }
        // Start the submitters;
        startLatch.countDown();

        // Wait for completion
        Uninterruptibles.awaitUninterruptibly(completionLatch, 1, TimeUnit.MINUTES);

        // Make sure they all report failure
        for (ListenableFuture<Void> future : futures) {
            try {
                future.get();
                fail();
            }
            catch (Exception ignored) {
            }
        }

        assertEquals(successCount.get(), 0);
        assertEquals(failureCount.get(), 100);
    }

    @Test
    public void testNoStackOverflow()
            throws Exception
    {
        AsyncSemaphore<Object, Void> asyncSemaphore = new AsyncSemaphore<>(1, executor, object -> Futures.immediateFuture(null));

        List<ListenableFuture<Void>> futures = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            futures.add(asyncSemaphore.submit(new Object()));
        }

        // Wait for completion
        Futures.allAsList(futures).get(1, TimeUnit.MINUTES);
    }

    @DataProvider
    public static Object[][] concurrency()
    {
        return new Object[][] {{1}, {2}, {3}, {5}};
    }

    @Test(dataProvider = "concurrency")
    public void testProcessAllEmptyList(int concurrency)
            throws Exception
    {
        ListenableFuture<List<Object>> result = AsyncSemaphore.processAll(ImmutableList.of(), (i) -> immediateCancelledFuture(), concurrency, directExecutor());
        assertThat(result).isDone();
        assertThat(result.get()).isEqualTo(ImmutableList.of());
    }

    @Test(dataProvider = "concurrency")
    public void testProcessAllSingleCallable(int concurrency)
            throws Exception
    {
        SettableFuture<String> future = SettableFuture.create();
        ListenableFuture<List<String>> result = AsyncSemaphore.processAll(ImmutableList.of(1), (i) -> future, concurrency, directExecutor());
        assertThat(result).isNotDone();
        future.set("value");
        assertThat(result).isDone();
        assertThat(result.get()).isEqualTo(ImmutableList.of("value"));
    }

    @Test(dataProvider = "concurrency")
    public void testProcessAllConcurrencyLimit(int concurrency)
            throws Exception
    {
        TestingTasks tasks = new TestingTasks(concurrency + 2);

        ListenableFuture<List<String>> result = AsyncSemaphore.processAll(tasks.getTasks(), tasks::submit, concurrency, directExecutor());

        assertThat(result).isNotDone();
        assertThat(tasks.getFutures()).hasSize(concurrency);

        tasks.getFutures().get(0).set("value0");
        assertThat(result).isNotDone();
        assertThat(tasks.getFutures()).hasSize(concurrency + 1);

        tasks.getFutures().get(1).set("value1");
        assertThat(result).isNotDone();
        assertThat(tasks.getFutures()).hasSize(concurrency + 2);

        tasks.getFutures().get(2).set("value2");
        assertThat(tasks.getFutures()).hasSize(concurrency + 2);

        for (int i = 3; i < tasks.getFutures().size(); i++) {
            tasks.getFutures().get(i).set("value" + i);
        }
        assertThat(result).isDone();

        assertThat(result.get()).isEqualTo(IntStream.range(0, tasks.getFutures().size()).mapToObj(i -> "value" + i).collect(toImmutableList()));
    }

    @Test(dataProvider = "concurrency")
    public void testProcessAllCallableFailure(int concurrency)
    {
        testProcessAllFailure(
                concurrency,
                () -> {
                    throw new RuntimeException("callable failed");
                },
                "callable failed");
    }

    @Test(dataProvider = "concurrency")
    public void testProcessAllFutureFailure(int concurrency)
    {
        testProcessAllFailure(
                concurrency,
                () -> immediateFailedFuture(new RuntimeException("future failed")),
                "future failed");
    }

    @Test(dataProvider = "concurrency")
    public void testProcessAllFutureCancellation(int concurrency)
    {
        testProcessAllFailure(
                concurrency,
                Futures::immediateCancelledFuture,
                "Task was cancelled");
    }

    @Test(dataProvider = "concurrency")
    public void testProcessAllCancellation(int concurrency)
    {
        TestingTasks tasks = new TestingTasks(concurrency + 1);

        ListenableFuture<List<String>> result = AsyncSemaphore.processAll(
                tasks.getTasks(),
                tasks::submit,
                concurrency,
                directExecutor());
        assertThat(result).isNotDone();
        result.cancel(true);
        assertThat(tasks.getFutures()).hasSize(concurrency);
        for (ListenableFuture<String> future : tasks.getFutures()) {
            assertThat(future).isCancelled();
        }

        tasks = new TestingTasks(concurrency + 2);
        result = AsyncSemaphore.processAll(
                tasks.getTasks(),
                tasks::submit,
                concurrency,
                directExecutor());
        assertThat(result).isNotDone();
        tasks.getFutures().get(0).set("value");
        assertThat(result).isNotDone();
        result.cancel(true);
        assertThat(tasks.getFutures()).hasSize(concurrency + 1);
        for (int i = 1; i < concurrency + 1; i++) {
            assertThat(tasks.getFutures().get(i)).isCancelled();
        }
    }

    private static void testProcessAllFailure(int concurrency, Supplier<ListenableFuture<String>> failure, String message)
    {
        TestingTasks tasks = new TestingTasks(concurrency);
        tasks.injectFailure(concurrency - 1, failure);
        ListenableFuture<List<String>> result = AsyncSemaphore.processAll(
                tasks.getTasks(),
                tasks::submit,
                concurrency,
                directExecutor());
        assertThat(result).isDone();
        assertThatFutureFailsWithMessageContaining(result, message);
        for (ListenableFuture<String> future : tasks.getFutures()) {
            assertThat(future).isCancelled();
        }

        tasks = new TestingTasks(concurrency + 1);
        tasks.injectFailure(concurrency, failure);
        result = AsyncSemaphore.processAll(
                tasks.getTasks(),
                tasks::submit,
                concurrency,
                directExecutor());
        assertThat(result).isNotDone();
        tasks.getFutures().get(0).set("value");
        assertThat(result).isDone();
        assertThatFutureFailsWithMessageContaining(result, message);
        for (int i = 1; i < concurrency; i++) {
            assertThat(tasks.getFutures().get(i)).isCancelled();
        }

        tasks = new TestingTasks(concurrency + 2);
        tasks.injectFailure(concurrency + 1, failure);
        result = AsyncSemaphore.processAll(
                tasks.getTasks(),
                tasks::submit,
                concurrency,
                directExecutor());
        assertThat(result).isNotDone();
        tasks.getFutures().get(0).set("value");
        assertThat(result).isNotDone();
        tasks.getFutures().get(1).set("value");
        assertThat(result).isDone();
        assertThatFutureFailsWithMessageContaining(result, message);
        for (int i = 2; i < concurrency; i++) {
            assertThat(tasks.getFutures().get(i)).isCancelled();
        }
    }

    private static void assertThatFutureFailsWithMessageContaining(Future<?> future, String message)
    {
        assertThat(future)
                .failsWithin(0, TimeUnit.SECONDS)
                .withThrowableOfType(Exception.class)
                .withMessageContaining(message);
    }

    private static class TestingTasks
    {
        private final List<Integer> tasks;
        private final List<SettableFuture<String>> futures = new CopyOnWriteArrayList<>();
        private final Map<Integer, Supplier<ListenableFuture<String>>> failures = new ConcurrentHashMap<>();

        private TestingTasks(int count)
        {
            this.tasks = IntStream.range(0, count)
                    .boxed()
                    .collect(toImmutableList());
        }

        public void injectFailure(int task, Supplier<ListenableFuture<String>> failure)
        {
            failures.put(task, failure);
        }

        public ListenableFuture<String> submit(Integer value)
        {
            Supplier<ListenableFuture<String>> failure = failures.get(value);
            if (failure != null) {
                return failure.get();
            }
            SettableFuture<String> future = SettableFuture.create();
            futures.add(future);
            return future;
        }

        public List<Integer> getTasks()
        {
            return tasks;
        }

        public List<SettableFuture<String>> getFutures()
        {
            return ImmutableList.copyOf(futures);
        }
    }

    private static RuntimeException assertFailedConcurrency(AtomicInteger concurrency)
    {
        int currentConcurrency = concurrency.incrementAndGet();
        assertLessThanOrEqual(currentConcurrency, 2);
        Uninterruptibles.sleepUninterruptibly(1, TimeUnit.MILLISECONDS);
        concurrency.decrementAndGet();
        throw new IllegalStateException();
    }

    private static FutureCallback<Object> completionCallback(AtomicInteger successCount, AtomicInteger failureCount, CountDownLatch completionLatch)
    {
        return new FutureCallback<Object>()
        {
            @Override
            public void onSuccess(@Nullable Object result)
            {
                successCount.incrementAndGet();
                completionLatch.countDown();
            }

            @Override
            public void onFailure(Throwable t)
            {
                failureCount.incrementAndGet();
                completionLatch.countDown();
            }
        };
    }

    private ListenableFuture<Void> submitTask(Runnable task)
    {
        return asVoid(executor.submit(task));
    }
}
