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

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.Uninterruptibles;
import org.testng.Assert;
import org.testng.annotations.Test;

import javax.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static com.google.common.util.concurrent.Futures.addCallback;
import static com.google.common.util.concurrent.MoreExecutors.listeningDecorator;
import static com.google.common.util.concurrent.MoreExecutors.newDirectExecutorService;
import static io.airlift.concurrent.Threads.daemonThreadsNamed;
import static io.airlift.testing.Assertions.assertLessThanOrEqual;
import static java.util.concurrent.Executors.newCachedThreadPool;

public class TestAsyncSemaphore
{
    private final ListeningExecutorService executor = listeningDecorator(newCachedThreadPool(daemonThreadsNamed("async-semaphore-%s")));

    @Test
    public void testInlineExecution()
            throws Exception
    {
        AsyncSemaphore<Runnable> asyncSemaphore = new AsyncSemaphore<>(1, executor, task -> newDirectExecutorService().submit(task));

        AtomicInteger count = new AtomicInteger();

        List<ListenableFuture<?>> futures = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            futures.add(asyncSemaphore.submit(count::incrementAndGet));
        }

        // Wait for completion
        Futures.allAsList(futures).get(1, TimeUnit.MINUTES);

        Assert.assertEquals(count.get(), 1000);
    }

    @Test
    public void testSingleThreadBoundedConcurrency()
            throws Exception
    {
        AsyncSemaphore<Runnable> asyncSemaphore = new AsyncSemaphore<>(1, executor, executor::submit);

        AtomicInteger count = new AtomicInteger();
        AtomicInteger concurrency = new AtomicInteger();

        List<ListenableFuture<?>> futures = new ArrayList<>();
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

        Assert.assertEquals(count.get(), 1000);
    }

    @Test
    public void testMultiThreadBoundedConcurrency()
            throws Exception
    {
        AsyncSemaphore<Runnable> asyncSemaphore = new AsyncSemaphore<>(2, executor, executor::submit);

        AtomicInteger count = new AtomicInteger();
        AtomicInteger concurrency = new AtomicInteger();

        List<ListenableFuture<?>> futures = new ArrayList<>();
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

        Assert.assertEquals(count.get(), 1000);
    }

    @Test
    public void testMultiSubmitters()
            throws Exception
    {
        AsyncSemaphore<Runnable> asyncSemaphore = new AsyncSemaphore<>(2, executor, executor::submit);

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

        Assert.assertEquals(count.get(), 100);
    }

    @Test
    public void testFailedTasks()
            throws Exception
    {
        AsyncSemaphore<Runnable> asyncSemaphore = new AsyncSemaphore<>(2, executor, executor::submit);

        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failureCount = new AtomicInteger();
        AtomicInteger concurrency = new AtomicInteger();
        CountDownLatch completionLatch = new CountDownLatch(1000);

        List<ListenableFuture<?>> futures = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            ListenableFuture<?> future = asyncSemaphore.submit(() -> assertFailedConcurrency(concurrency));
            addCallback(future, completionCallback(successCount, failureCount, completionLatch));
            futures.add(future);
        }

        // Wait for all tasks and callbacks to complete
        completionLatch.await(1, TimeUnit.MINUTES);

        for (ListenableFuture<?> future : futures) {
            try {
                future.get();
                Assert.fail();
            }
            catch (Exception ignored) {
            }
        }

        Assert.assertEquals(successCount.get(), 0);
        Assert.assertEquals(failureCount.get(), 1000);
    }

    @Test
    public void testFailedTaskSubmission()
            throws Exception
    {
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failureCount = new AtomicInteger();
        AtomicInteger concurrency = new AtomicInteger();
        CountDownLatch completionLatch = new CountDownLatch(1000);
        AsyncSemaphore<Runnable> asyncSemaphore = new AsyncSemaphore<>(2, executor, task -> {
            throw assertFailedConcurrency(concurrency);
        });

        List<ListenableFuture<?>> futures = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            // Should never execute this future
            ListenableFuture<?> future = asyncSemaphore.submit(Assert::fail);
            addCallback(future, completionCallback(successCount, failureCount, completionLatch));
            futures.add(future);
        }

        // Wait for all tasks and callbacks to complete
        completionLatch.await(1, TimeUnit.MINUTES);

        for (ListenableFuture<?> future : futures) {
            try {
                future.get();
                Assert.fail();
            }
            catch (Exception ignored) {
            }
        }

        Assert.assertEquals(successCount.get(), 0);
        Assert.assertEquals(failureCount.get(), 1000);
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

        AsyncSemaphore<Runnable> asyncSemaphore = new AsyncSemaphore<>(2, executor, task -> {
            throw assertFailedConcurrency(concurrency);
        });

        Queue<ListenableFuture<?>> futures = new ConcurrentLinkedQueue<>();
        for (int i = 0; i < 100; i++) {
            executor.execute(() -> {
                Uninterruptibles.awaitUninterruptibly(startLatch, 1, TimeUnit.MINUTES);
                // Should never execute this future
                ListenableFuture<?> future = asyncSemaphore.submit(Assert::fail);
                futures.add(future);
                addCallback(future, completionCallback(successCount, failureCount, completionLatch));
            });
        }
        // Start the submitters;
        startLatch.countDown();

        // Wait for completion
        Uninterruptibles.awaitUninterruptibly(completionLatch, 1, TimeUnit.MINUTES);

        // Make sure they all report failure
        for (ListenableFuture<?> future : futures) {
            try {
                future.get();
                Assert.fail();
            }
            catch (Exception ignored) {
            }
        }

        Assert.assertEquals(successCount.get(), 0);
        Assert.assertEquals(failureCount.get(), 100);
    }

    @Test
    public void testNoStackOverflow()
            throws Exception
    {
        AsyncSemaphore<Object> asyncSemaphore = new AsyncSemaphore<>(1, executor, object -> Futures.immediateFuture(null));

        List<ListenableFuture<?>> futures = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            futures.add(asyncSemaphore.submit(new Object()));
        }

        // Wait for completion
        Futures.allAsList(futures).get(1, TimeUnit.MINUTES);
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
}
