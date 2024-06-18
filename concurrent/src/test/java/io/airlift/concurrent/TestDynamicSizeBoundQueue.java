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

import com.google.common.collect.ConcurrentHashMultiset;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.Uninterruptibles;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;

@TestInstance(TestInstance.Lifecycle.PER_METHOD)
public class TestDynamicSizeBoundQueue
{
    private ListeningExecutorService executorService;

    @BeforeEach
    public void setUp()
    {
        executorService = MoreExecutors.listeningDecorator(Executors.newCachedThreadPool());
    }

    @AfterEach
    public void tearDown()
    {
        executorService.shutdownNow();
    }

    @Test
    public void testBasicOfferPoll()
            throws InterruptedException
    {
        DynamicSizeBoundQueue<String> queue = new DynamicSizeBoundQueue<>(3, String::length);

        // Empty queue
        assertThat(queue.getMaxSize()).isEqualTo(3);
        assertThat(queue.getSize()).isZero();
        assertThat(queue.poll()).isNull();
        assertThat(queue.poll(1, TimeUnit.MILLISECONDS))
                .as("Just use any short poll timeout to test the API")
                .isNull();

        // Insert a single element of length 1
        assertThat(queue.offer("a")).isTrue();
        assertThat(queue.getMaxSize()).isEqualTo(3);
        assertThat(queue.getSize()).isEqualTo(1);
        assertThat(queue.poll()).isEqualTo("a");
        assertThat(queue.poll())
                .as("No more elements")
                .isNull();
        assertThat(queue.getSize()).isZero();

        // Insert 2 elements that fill up the queue exactly to capacity
        assertThat(queue.offer("a")).isTrue();
        assertThat(queue.offer("bb")).isTrue();
        assertThat(queue.offer("c"))
                .as("Queue already full")
                .isFalse();
        assertThat(queue.getMaxSize()).isEqualTo(3);
        assertThat(queue.getSize()).isEqualTo(3);
        assertThat(queue.poll()).isEqualTo("a");
        assertThat(queue.poll()).isEqualTo("bb");
        assertThat(queue.poll())
                .as("No more elements")
                .isNull();
        assertThat(queue.getSize()).isZero();

        // Overfill queue
        assertThat(queue.offer("aa")).isTrue();
        assertThat(queue.offer("bbb")).isTrue();
        assertThat(queue.offer("c"))
                .as("Queue already over capacity")
                .isFalse();
        assertThat(queue.getMaxSize()).isEqualTo(3);
        assertThat(queue.getSize()).isEqualTo(5);
        assertThat(queue.poll()).isEqualTo("aa");
        assertThat(queue.poll()).isEqualTo("bbb");
        assertThat(queue.poll())
                .as("No more elements")
                .isNull();
        assertThat(queue.getSize()).isZero();
    }

    @Test
    public void testOversizeElement()
    {
        DynamicSizeBoundQueue<String> queue = new DynamicSizeBoundQueue<>(3, String::length);

        assertThat(queue.offer("aaaaa"))
                .as("Queue always allows the insertion of an element as long as any space is available")
                .isTrue();
        assertThat(queue.offer("b"))
                .as("Queue already over capacity")
                .isFalse();
        assertThat(queue.getSize()).isEqualTo(5);

        assertThat(queue.poll()).isEqualTo("aaaaa");
        assertThat(queue.poll())
                .as("No more elements")
                .isNull();
        assertThat(queue.getSize()).isZero();
    }

    @Test
    public void testOfferSizeOverflow()
    {
        DynamicSizeBoundQueue<Long> queue = new DynamicSizeBoundQueue<>(Long.MAX_VALUE, element -> element);

        assertThat(queue.offer(Long.MAX_VALUE - 1))
                .isTrue();

        assertThat(queue.offer(2L))
                .as("Element of size 2 should be rejected due to size numeric overflow")
                .isFalse();
        assertThat(queue.getSize())
                .as("Size should remain unchanged")
                .isEqualTo(Long.MAX_VALUE - 1);

        assertThat(queue.offer(Long.MAX_VALUE))
                .as("Element of size Long.MAX_VALUE should be rejected due to size numeric overflow")
                .isFalse();
        assertThat(queue.getSize())
                .as("Size should remain unchanged")
                .isEqualTo(Long.MAX_VALUE - 1);

        assertThat(queue.offer(1L))
                .as("Should be able to fill capacity up to Long.MAX_VALUE")
                .isTrue();
        assertThat(queue.getSize())
                .as("Size should be at capacity")
                .isEqualTo(Long.MAX_VALUE);

        // Empty the queue
        assertThat(queue.poll())
                .isEqualTo(Long.MAX_VALUE - 1);
        assertThat(queue.poll())
                .isEqualTo(1L);

        assertThat(queue.offer(Long.MAX_VALUE))
                .as("Element of size Long.MAX_VALUE should be accepted for an empty queue")
                .isTrue();
        assertThat(queue.getSize())
                .isEqualTo(Long.MAX_VALUE);
    }

    @Test
    public void testForcePutSizeOverflow()
    {
        DynamicSizeBoundQueue<Long> queue = new DynamicSizeBoundQueue<>(Long.MAX_VALUE, element -> element);

        assertThat(queue.offer(Long.MAX_VALUE - 1))
                .isTrue();

        assertThatThrownBy(() -> queue.forcePut(2L))
                .as("Element of size 2 should be rejected due to size numeric overflow")
                .isInstanceOf(IllegalStateException.class);
        assertThat(queue.getSize())
                .as("Size should remain unchanged")
                .isEqualTo(Long.MAX_VALUE - 1);
    }

    @Test
    public void testZeroSizeElement()
    {
        DynamicSizeBoundQueue<String> queue = new DynamicSizeBoundQueue<>(1, String::length);

        // All forms of insertion should fail if the element size is zero
        assertThatThrownBy(() -> queue.offer("")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> queue.offer("", 10, TimeUnit.SECONDS)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> queue.offerWithBackoff("")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> queue.put("")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> queue.forcePut("")).isInstanceOf(IllegalArgumentException.class);

        assertThat(queue.getSize()).isZero();
    }

    @Test
    public void testNegativeElementSizes()
    {
        DynamicSizeBoundQueue<String> queue = new DynamicSizeBoundQueue<>(1, ignored -> -1L);

        // All forms of insertion should fail if the element size is negative
        assertThatThrownBy(() -> queue.offer("a")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> queue.offer("", 10, TimeUnit.SECONDS)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> queue.offerWithBackoff("a")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> queue.put("a")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> queue.forcePut("a")).isInstanceOf(IllegalArgumentException.class);

        assertThat(queue.getSize()).isZero();
    }

    @Test
    public void testUnstableElementSize()
    {
        AtomicLong elementSizeToReport = new AtomicLong();
        DynamicSizeBoundQueue<String> queue = new DynamicSizeBoundQueue<>(3, ignored -> elementSizeToReport.get());

        elementSizeToReport.set(1);
        assertThat(queue.offer("a")).isTrue();
        assertThat(queue.getSize()).isEqualTo(1);

        elementSizeToReport.set(100);
        assertThat(queue.poll()).isEqualTo("a");
        assertThat(queue.getSize())
                .as("Even though the element size reported a new value, the original element size is respected")
                .isZero();

        elementSizeToReport.set(1);
        assertThat(queue.offer("b")).isTrue();
        assertThat(queue.getSize()).isEqualTo(1);

        elementSizeToReport.set(10);
        assertThat(queue.offer("c")).isTrue();
        assertThat(queue.getSize()).isEqualTo(11);

        elementSizeToReport.set(5);
        assertThat(queue.poll()).isEqualTo("b");
        assertThat(queue.getSize())
                .as("Even though the element size reported a new value, the original element size is respected")
                .isEqualTo(10);

        elementSizeToReport.set(-1);
        assertThat(queue.poll()).isEqualTo("c");
        assertThat(queue.getSize())
                .as("Even though the element size reported a new negative value, the original element size is respected")
                .isZero();
    }

    @Test
    public void testNullElement()
    {
        DynamicSizeBoundQueue<String> queue = new DynamicSizeBoundQueue<>(1, value -> 1);
        assertThatThrownBy(() -> queue.offer(null))
                .as("Queue does not permit null elements, even if the element size function does")
                .isInstanceOf(NullPointerException.class);

        assertThat(queue.getSize())
                .as("Queue size should not be changed after failing on a null element")
                .isZero();
    }

    @Test
    public void testBlockingOffer()
            throws ExecutionException, InterruptedException, TimeoutException
    {
        CountDownLatch awaitDequeueLatch = new CountDownLatch(1);
        DynamicSizeBoundQueue<String> queue = new DynamicSizeBoundQueue<>(3, String::length)
        {
            @Override
            void preDequeueAwaitHook()
            {
                awaitDequeueLatch.countDown();
            }
        };

        assertThat(queue.offer("aaa"))
                .as("Fill the queue")
                .isTrue();

        ListenableFuture<Boolean> offerFuture = executorService.submit(() -> queue.offer("b", 10, TimeUnit.SECONDS));

        // Wait for the offering thread to block for space
        Uninterruptibles.awaitUninterruptibly(awaitDequeueLatch, 10, TimeUnit.SECONDS);
        assertThat(offerFuture.isDone()).isFalse();

        assertThat(queue.poll())
                .as("Create space in the queue")
                .isEqualTo("aaa");

        assertThat(offerFuture.get(10, TimeUnit.SECONDS))
                .as("Offer should complete quickly once space becomes available")
                .isTrue();
        assertThat(queue.poll())
                .as("Should be able to extract the new element from the queue")
                .isEqualTo("b");
    }

    @Test
    public void testBlockingOfferTimeout()
            throws ExecutionException, InterruptedException, TimeoutException
    {
        DynamicSizeBoundQueue<String> queue = new DynamicSizeBoundQueue<>(3, String::length);

        assertThat(queue.offer("aaa"))
                .as("Fill the queue")
                .isTrue();

        ListenableFuture<Boolean> offerFuture = executorService.submit(() -> queue.offer("b", 10, TimeUnit.MILLISECONDS));

        assertThat(offerFuture.get(10, TimeUnit.SECONDS))
                .as("Offer should timeout")
                .isFalse();
        assertThat(queue.getSize())
                .as("Queue size should remain the same")
                .isEqualTo(3);
    }

    @Test
    public void testPut()
            throws ExecutionException, InterruptedException, TimeoutException
    {
        CountDownLatch awaitDequeueLatch = new CountDownLatch(1);
        DynamicSizeBoundQueue<String> queue = new DynamicSizeBoundQueue<>(3, String::length)
        {
            @Override
            void preDequeueAwaitHook()
            {
                awaitDequeueLatch.countDown();
            }
        };

        assertThat(queue.offer("aaa"))
                .as("Fill the queue")
                .isTrue();

        ListenableFuture<?> putFuture = executorService.submit(() -> {
            try {
                queue.put("b");
            }
            catch (InterruptedException e) {
                fail("Interrupted");
            }
        });

        // Wait for the offering thread to block for space
        Uninterruptibles.awaitUninterruptibly(awaitDequeueLatch, 10, TimeUnit.SECONDS);
        assertThat(putFuture.isDone()).isFalse();

        assertThat(queue.poll())
                .as("Create space in the queue")
                .isEqualTo("aaa");

        // Put should complete quickly once space becomes available
        putFuture.get(10, TimeUnit.SECONDS);
        assertThat(queue.poll())
                .as("Should be able to extract the new element from the queue")
                .isEqualTo("b");
    }

    @Test
    public void testOfferWithBackoff()
    {
        DynamicSizeBoundQueue<String> queue = new DynamicSizeBoundQueue<>(3, String::length);

        for (int i = 0; i < 3; i++) {
            assertThat(queue.offerWithBackoff("a"))
                    .as("No backoff returned while space exists")
                    .isEmpty();
        }
        assertThat(queue.getSize())
                .as("Queue is at capacity")
                .isEqualTo(queue.getMaxSize());

        Optional<ListenableFuture<Void>> backoffResult1 = queue.offerWithBackoff("b");
        assertThat(backoffResult1)
                .as("Queue should provide a backoff future when at capacity")
                .isPresent();
        assertThat(backoffResult1.get()).isNotDone();

        Optional<ListenableFuture<Void>> backoffResult2 = queue.offerWithBackoff("c");
        assertThat(backoffResult2)
                .as("Queue should provide a backoff future when at capacity")
                .isPresent();
        assertThat(backoffResult2.get()).isNotDone();

        assertThat(queue.poll())
                .as("Dequeue an element to make some space")
                .isEqualTo("a");
        assertThat(queue.getSize())
                .as("Space is now available")
                .isLessThan(queue.getMaxSize());

        // Both backoff futures should complete when any space is made available
        assertThat(backoffResult1.get()).isDone();
        assertThat(backoffResult2.get()).isDone();
    }

    @Test
    public void testBlockingPoll()
            throws ExecutionException, InterruptedException, TimeoutException
    {
        CountDownLatch awaitEnqueueLatch = new CountDownLatch(1);
        DynamicSizeBoundQueue<String> queue = new DynamicSizeBoundQueue<>(3, String::length)
        {
            @Override
            void preEnqueueAwaitHook()
            {
                awaitEnqueueLatch.countDown();
            }
        };

        ListenableFuture<String> pollFuture = executorService.submit(() -> queue.poll(10, TimeUnit.SECONDS));

        // Wait for the polling thread to block for data
        Uninterruptibles.awaitUninterruptibly(awaitEnqueueLatch, 10, TimeUnit.SECONDS);
        assertThat(pollFuture.isDone()).isFalse();

        queue.offer("a");
        assertThat(pollFuture.get(10, TimeUnit.SECONDS))
                .as("Should be able to extract new element as soon as it is added")
                .isEqualTo("a");
    }

    @Test
    public void testBlockingPollTimeout()
            throws ExecutionException, InterruptedException, TimeoutException
    {
        DynamicSizeBoundQueue<String> queue = new DynamicSizeBoundQueue<>(3, String::length);

        ListenableFuture<String> pollFuture = executorService.submit(() -> queue.poll(10, TimeUnit.MILLISECONDS));

        assertThat(pollFuture.get(10, TimeUnit.SECONDS))
                .as("Should timeout awaiting a new element")
                .isNull();
    }

    @Test
    public void testTake()
            throws ExecutionException, InterruptedException, TimeoutException
    {
        CountDownLatch awaitEnqueueLatch = new CountDownLatch(1);
        DynamicSizeBoundQueue<String> queue = new DynamicSizeBoundQueue<>(3, String::length)
        {
            @Override
            void preEnqueueAwaitHook()
            {
                awaitEnqueueLatch.countDown();
            }
        };

        ListenableFuture<String> takeFuture = executorService.submit(() -> {
            try {
                return queue.take();
            }
            catch (InterruptedException e) {
                throw new AssertionError(e);
            }
        });

        // Wait for the polling thread to block for a new element
        Uninterruptibles.awaitUninterruptibly(awaitEnqueueLatch, 10, TimeUnit.SECONDS);
        assertThat(takeFuture.isDone()).isFalse();

        assertThat(queue.offer("a"))
                .as("Insert new element")
                .isTrue();

        assertThat(takeFuture.get(10, TimeUnit.SECONDS))
                .as("Take should return quickly once a new element becomes available")
                .isEqualTo("a");
    }

    @Test
    public void testConcurrency()
            throws ExecutionException, InterruptedException, TimeoutException
    {
        DynamicSizeBoundQueue<String> queue = new DynamicSizeBoundQueue<>(3, String::length);

        // Concurrent spin loop submissions
        for (int i = 0; i < 10; i++) {
            executorService.execute(() -> {
                int offered = 0;
                while (offered < 200) {
                    if (queue.offer("a")) {
                        offered++;
                    }
                }
            });
        }
        int spinLoopSubmissions = 10 * 200;

        // Concurrent blocking offer submissions
        for (int i = 0; i < 10; i++) {
            executorService.execute(() -> {
                try {
                    int offered = 0;
                    while (offered < 200) {
                        if (queue.offer("bb", 100, TimeUnit.MILLISECONDS)) {
                            offered++;
                        }
                    }
                }
                catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    fail("Interrupted");
                }
            });
        }
        int blockingOfferSubmissions = 10 * 200;

        // Concurrent blocking put submissions
        for (int i = 0; i < 10; i++) {
            executorService.execute(() -> {
                for (int j = 0; j < 200; j++) {
                    try {
                        queue.put("ccc");
                    }
                    catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        fail("Interrupted");
                    }
                }
            });
        }
        int blockingPutSubmissions = 10 * 200;

        // Concurrent force put submissions
        for (int i = 0; i < 10; i++) {
            executorService.execute(() -> {
                for (int j = 0; j < 200; j++) {
                    queue.forcePut("d");
                }
            });
        }
        int forcePutSubmissions = 10 * 200;

        // Concurrent backoff future submissions
        for (int i = 0; i < 10; i++) {
            executorService.execute(() -> {
                for (int j = 0; j < 200; j++) {
                    Optional<ListenableFuture<Void>> backoffFuture = queue.offerWithBackoff("ee");
                    while (backoffFuture.isPresent()) {
                        Futures.getUnchecked(backoffFuture.get());
                        backoffFuture = queue.offerWithBackoff("ee");
                    }
                }
            });
        }
        int backoffFutureSubmissions = 10 * 200;

        int totalSubmissions = spinLoopSubmissions + blockingOfferSubmissions + blockingPutSubmissions + forcePutSubmissions + backoffFutureSubmissions;

        // Concurrent element pollers
        List<ListenableFuture<?>> pollFutures = new ArrayList<>();
        ConcurrentHashMultiset<String> dequeued = ConcurrentHashMultiset.create();
        for (int i = 0; i < 10; i++) {
            pollFutures.add(executorService.submit(() -> {
                try {
                    while (dequeued.size() < totalSubmissions) {
                        Optional.ofNullable(queue.poll(1, TimeUnit.MILLISECONDS))
                                .ifPresent(dequeued::add);
                    }
                }
                catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    fail("Interrupted");
                }
            }));
        }

        // Await polling the expected number of results
        Futures.allAsList(pollFutures).get(10, TimeUnit.SECONDS);

        assertThat(queue.getSize())
                .as("Queue should be empty after pulling everything out")
                .isZero();
        assertThat(dequeued.size()).isEqualTo(totalSubmissions);
        assertThat(dequeued.count("a")).isEqualTo(spinLoopSubmissions);
        assertThat(dequeued.count("bb")).isEqualTo(blockingOfferSubmissions);
        assertThat(dequeued.count("ccc")).isEqualTo(blockingPutSubmissions);
        assertThat(dequeued.count("d")).isEqualTo(forcePutSubmissions);
        assertThat(dequeued.count("ee")).isEqualTo(backoffFutureSubmissions);
    }
}
