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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Verify.verify;
import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElseGet;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Ticker;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.google.errorprone.annotations.ThreadSafe;
import jakarta.annotation.Nullable;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.ToLongFunction;

/**
 * Size constrained queue that utilizes a dynamic element size function. To prevent
 * starvation for adding large elements, the queue will only block new elements if
 * the total size has already been reached or exceeded. This means in the normal
 * case, the queue should be no larger than the max size plus the size of one element.
 * Callers also have the additional option to force insert further elements without
 * regard for size constraints. In the current implementation, elements are required
 * to have positive sizes (they cannot have zero size). This implementation is designed
 * to closely mirror the method signatures of {@link java.util.concurrent.BlockingQueue}.
 */
@ThreadSafe
public class DynamicSizeBoundQueue<T> {
    private final AtomicLong size = new AtomicLong();
    private final Queue<ElementAndSize<T>> queue = new ConcurrentLinkedQueue<>();
    private final AtomicReference<SettableFuture<Void>> enqueueFuture = new AtomicReference<>();
    private final AtomicReference<SettableFuture<Void>> dequeueFuture = new AtomicReference<>();

    private final long maxSize;
    private final ToLongFunction<T> elementSizeFunction;
    private final Ticker ticker;

    public DynamicSizeBoundQueue(long maxSize, ToLongFunction<T> elementSizeFunction) {
        this(maxSize, elementSizeFunction, Ticker.systemTicker());
    }

    public DynamicSizeBoundQueue(long maxSize, ToLongFunction<T> elementSizeFunction, Ticker ticker) {
        checkArgument(maxSize > 0, "maxSize must be positive");
        this.maxSize = maxSize;
        this.elementSizeFunction = requireNonNull(elementSizeFunction, "elementSizeFunction is null");
        this.ticker = requireNonNull(ticker, "ticker is null");
    }

    public long getMaxSize() {
        return maxSize;
    }

    /**
     * Gets the current size of the queue. The size is guaranteed to be no larger than max size plus
     * the size of one element if {@link DynamicSizeBoundQueue#forcePut(Object)} is not used.
     */
    public long getSize() {
        return size.get();
    }

    public boolean offer(T element) {
        long elementSize = elementSizeFunction.applyAsLong(element);
        return offer(element, elementSize);
    }

    private boolean offer(T element, long elementSize) {
        requireNonNull(element, "element is null");
        checkArgument(elementSize > 0, "element size must be positive");
        if (!tryAcquireSizeReservation(elementSize)) {
            return false;
        }
        queue.add(new ElementAndSize<>(element, elementSize));
        notifyIfNecessary(enqueueFuture);
        return true;
    }

    private boolean tryAcquireSizeReservation(long elementSize) {
        // Add the element as long as there is any space available
        if (size.get() >= maxSize) {
            return false;
        }

        long newSize;
        try {
            newSize = getAndAddOverflowChecked(size, elementSize);
        } catch (ArithmeticException e) { // Numeric overflow
            // While numeric overflow is extremely unlikely given typical numerical sizes,
            // even the largest possible element of size Long.MAX_VALUE can eventually fit
            // without numeric overflow as long as the queue can be emptied.
            return false;
        }

        if (newSize >= maxSize) {
            verify(size.addAndGet(-elementSize) >= 0);
            return false;
        }
        return true;
    }

    /**
     * Version of {@link java.util.concurrent.atomic.AtomicLong#getAndAdd} that throws {@link ArithmeticException}
     * on numeric overflow. This is slightly less efficient than the normal getAndAdd (which often has intrinsic
     * support). If this ever becomes a performance bottleneck, it is possible to use the original getAndAdd if
     * the caller can guarantee no risk of numeric overflow.
     */
    private static long getAndAddOverflowChecked(AtomicLong atomicLong, long delta) {
        return atomicLong.getAndAccumulate(delta, Math::addExact);
    }

    public boolean offer(T element, long timeout, TimeUnit unit) throws InterruptedException {
        long elementSize = elementSizeFunction.applyAsLong(element);
        long remainingTimeoutNs = unit.toNanos(timeout);
        while (!offer(element, elementSize)) {
            ListenableFuture<Void> future = getOrCreateFuture(dequeueFuture);
            // Check again in case we already missed the relevant dequeue event
            if (offer(element, elementSize)) {
                break;
            }
            long startTimeNs = ticker.read();
            if (remainingTimeoutNs <= 0 || !awaitDequeueFuture(future, remainingTimeoutNs, NANOSECONDS)) {
                // Timed out
                return false;
            }
            remainingTimeoutNs -= ticker.read() - startTimeNs;
        }
        return true;
    }

    public void put(T element) throws InterruptedException {
        long elementSize = elementSizeFunction.applyAsLong(element);
        while (!offer(element, elementSize)) {
            ListenableFuture<Void> future = getOrCreateFuture(dequeueFuture);
            // Check again in case we already missed the relevant dequeue event
            if (offer(element, elementSize)) {
                break;
            }
            awaitDequeueFuture(future);
        }
    }

    /**
     * Enqueue the element if there is space, otherwise returns a ListenableFuture that will complete
     * when space becomes available for the element. If a future is returned, the element was not inserted.
     */
    public Optional<ListenableFuture<Void>> offerWithBackoff(T element) {
        long elementSize = elementSizeFunction.applyAsLong(element);
        if (offer(element, elementSize)) {
            return Optional.empty();
        }
        ListenableFuture<Void> future = getOrCreateFuture(dequeueFuture);
        // Check again in case we already missed the relevant dequeue event
        if (offer(element, elementSize)) {
            return Optional.empty();
        }
        return Optional.of(Futures.nonCancellationPropagating(future));
    }

    /**
     * Insert without regard to the max size (potentially exceeding the max limit). This can throw an
     * {@link IllegalStateException} if the forced element triggers a numeric overflow, in which case
     * the element is not inserted.
     */
    public void forcePut(T element) {
        long elementSize = elementSizeFunction.applyAsLong(element);
        checkArgument(elementSize > 0, "element size must be positive");
        try {
            getAndAddOverflowChecked(size, elementSize);
        } catch (ArithmeticException e) { // Numeric overflow
            throw new IllegalStateException("Forced element triggered queue size numeric overflow");
        }
        queue.add(new ElementAndSize<>(element, elementSize));
        notifyIfNecessary(enqueueFuture);
    }

    @Nullable
    public T poll() {
        ElementAndSize<T> elementAndSize = queue.poll();
        if (elementAndSize == null) {
            return null;
        }

        verify(size.addAndGet(-elementAndSize.size()) >= 0);
        notifyIfNecessary(dequeueFuture);
        return elementAndSize.element();
    }

    public T poll(long timeout, TimeUnit unit) throws InterruptedException {
        long remainingTimeoutNs = unit.toNanos(timeout);
        while (true) {
            T element = poll();
            if (element != null) {
                return element;
            }

            ListenableFuture<Void> future = getOrCreateFuture(enqueueFuture);
            // Check again in case we already missed the relevant enqueue event
            element = poll();
            if (element != null) {
                return element;
            }

            long startTimeNs = ticker.read();
            if (remainingTimeoutNs <= 0 || !awaitEnqueueFuture(future, remainingTimeoutNs, NANOSECONDS)) {
                // Timed out
                return null;
            }
            remainingTimeoutNs -= ticker.read() - startTimeNs;
        }
    }

    public T take() throws InterruptedException {
        while (true) {
            T element = poll();
            if (element != null) {
                return element;
            }

            ListenableFuture<Void> future = getOrCreateFuture(enqueueFuture);
            // Check again in case we already missed the relevant enqueue event
            element = poll();
            if (element != null) {
                return element;
            }

            awaitEnqueueFuture(future);
        }
    }

    private static ListenableFuture<Void> getOrCreateFuture(AtomicReference<SettableFuture<Void>> reference) {
        return reference.updateAndGet(current -> requireNonNullElseGet(current, SettableFuture::create));
    }

    private static void notifyIfNecessary(AtomicReference<SettableFuture<Void>> reference) {
        SettableFuture<?> future = reference.getAndSet(null);
        if (future != null) {
            future.set(null);
        }
    }

    @VisibleForTesting
    void preEnqueueAwaitHook() {}

    @VisibleForTesting
    void preDequeueAwaitHook() {}

    private void awaitDequeueFuture(Future<?> future) throws InterruptedException {
        preDequeueAwaitHook();
        awaitFutureUnchecked(future);
    }

    private boolean awaitDequeueFuture(Future<?> future, long timeout, TimeUnit timeUnit) throws InterruptedException {
        preDequeueAwaitHook();
        return awaitFutureUnchecked(future, timeout, timeUnit);
    }

    private void awaitEnqueueFuture(Future<?> future) throws InterruptedException {
        preEnqueueAwaitHook();
        awaitFutureUnchecked(future);
    }

    private boolean awaitEnqueueFuture(Future<?> future, long timeout, TimeUnit timeUnit) throws InterruptedException {
        preEnqueueAwaitHook();
        return awaitFutureUnchecked(future, timeout, timeUnit);
    }

    private static void awaitFutureUnchecked(Future<?> future) throws InterruptedException {
        try {
            future.get();
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    private static boolean awaitFutureUnchecked(Future<?> future, long timeout, TimeUnit timeUnit)
            throws InterruptedException {
        try {
            future.get(timeout, timeUnit);
            return true;
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        } catch (TimeoutException e) {
            return false;
        }
    }

    private record ElementAndSize<T>(T element, long size) {
        private ElementAndSize {
            requireNonNull(element, "element is null");
            checkArgument(size > 0, "size must be positive");
        }
    }
}
