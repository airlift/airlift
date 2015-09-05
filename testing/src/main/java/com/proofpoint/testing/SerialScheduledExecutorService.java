/*
 * Copyright 2012 Mark Kent
 *
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
package com.proofpoint.testing;

import com.google.common.base.Ticker;
import com.google.common.collect.ImmutableList;
import com.google.common.primitives.Longs;
import com.google.common.util.concurrent.Futures;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.PriorityQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Delayed;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.Lists.newArrayList;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

/**
 * Implements ScheduledExecutorService with a controllable time elapse. Tasks are run
 * in the context of the thread advancing time.
 * <p>
 * Tasks are modelled as instantaneous events; tasks scheduled to be run at the same
 * instant will be run in the order of their registration.
 */
public class SerialScheduledExecutorService
        implements ScheduledExecutorService
{
    private final TestingTicker ticker = new TestingTicker();
    private final PriorityQueue<SerialScheduledFuture<?>> futureTasks = new PriorityQueue<>();
    private Collection<SerialScheduledFuture<?>> tasks = futureTasks;
    private boolean isShutdown = false;

    @Override
    public void execute(Runnable runnable)
    {
        requireNonNull(runnable, "Task object is null");
        try {
            runnable.run();
        }
        catch (Throwable ignored) {
        }
    }

    @Override
    public void shutdown()
    {
        isShutdown = true;
    }

    @Override
    public List<Runnable> shutdownNow()
    {
        shutdown();
        // Note: This doesn't seem to be quite right. It seems like I should return the unexecuted tasks that
        // were scheduled on the ScheduledExecutorService, but I don't know how to turn a Callable into a
        // Runnable (which is required since future tasks can be scheduled as Callables).
        return Collections.emptyList();
    }

    @Override
    public boolean isShutdown()
    {
        return isShutdown;
    }

    @Override
    public boolean isTerminated()
    {
        return isShutdown();
    }

    @Override
    public boolean awaitTermination(long l, TimeUnit timeUnit)
            throws InterruptedException
    {
        return true;
    }

    @Override
    public <T> Future<T> submit(Callable<T> tCallable)
    {
        requireNonNull(tCallable, "Task object is null");
        try {
            return Futures.immediateFuture(tCallable.call());
        }
        catch (Exception e) {
            return Futures.immediateFailedFuture(e);
        }
    }

    @Override
    public <T> Future<T> submit(Runnable runnable, T t)
    {
        requireNonNull(runnable, "Task object is null");
        try {
            runnable.run();
            return Futures.immediateFuture(t);
        }
        catch (Exception e) {
            return Futures.immediateFailedFuture(e);
        }
    }

    @Override
    public Future<?> submit(Runnable runnable)
    {
        requireNonNull(runnable, "Task object is null");
        return submit(runnable, null);
    }


    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> callables)
            throws InterruptedException
    {
        requireNonNull(callables, "Task object list is null");
        ImmutableList.Builder<Future<T>> resultBuilder = ImmutableList.builder();
        for (Callable<T> callable : callables) {
            try {
                resultBuilder.add(Futures.immediateFuture(callable.call()));
            }
            catch (Exception e) {
                resultBuilder.add(Futures.<T>immediateFailedFuture(e));
            }
        }
        return resultBuilder.build();
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> callables, long l, TimeUnit timeUnit)
            throws InterruptedException
    {
        requireNonNull(callables, "Task object list is null");
        return invokeAll(callables);
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> callables)
            throws InterruptedException, ExecutionException
    {
        requireNonNull(callables, "callables is null");
        checkArgument(!callables.isEmpty(), "callables is empty");
        try {
            return callables.iterator().next().call();
        }
        catch (Exception e) {
            throw new ExecutionException(e);
        }
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> callables, long l, TimeUnit timeUnit)
            throws InterruptedException, ExecutionException, TimeoutException
    {
        return invokeAny(callables);
    }

    /**
     * Advance time by the given quantum.
     * <p>
     * Scheduled tasks due for execution will be executed in the caller's thread.
     *
     * @param quantum the amount of time to advance
     * @param timeUnit the unit of the quantum amount
     */
    public void elapseTime(long quantum, TimeUnit timeUnit)
    {
        checkArgument(quantum > 0, "Time quantum must be a positive number");
        checkState(!isShutdown, "Trying to elapse time after shutdown");

        elapseTime(toNanos(quantum, timeUnit), ticker);
    }

    /**
     * Advance time by one nanosecond less than the given quantum.
     * <p>
     * Scheduled tasks due for execution will be executed in the caller's thread.
     *
     * @param quantum the amount of time to advance one nanosecond short of
     * @param timeUnit the unit of the quantum amount
     */
    public void elapseTimeNanosecondBefore(long quantum, TimeUnit timeUnit)
    {
        checkArgument(quantum > 0, "Time quantum must be a positive number");
        checkState(!isShutdown, "Trying to elapse time after shutdown");

        elapseTime(toNanos(quantum, timeUnit) - 1, ticker);
    }

    /**
     * Returns a {@link com.google.common.base.Ticker} that is advanced by {@link #elapseTime}
     */
    public Ticker getTicker() {
        return ticker;
    }

    @Override
    public ScheduledFuture<?> schedule(Runnable runnable, long l, TimeUnit timeUnit)
    {
        requireNonNull(runnable, "Task object is null");
        checkArgument(l >= 0, "Delay must not be negative");
        SerialScheduledFuture<?> future = new SerialScheduledFuture<>(new FutureTask<Void>(runnable, null), toNanos(l, timeUnit));
        if (l == 0) {
            future.task.run();
        }
        else {
            tasks.add(future);
        }
        return future;
    }

    @Override
    public <V> ScheduledFuture<V> schedule(Callable<V> vCallable, long l, TimeUnit timeUnit)
    {
        requireNonNull(vCallable, "Task object is null");
        checkArgument(l >= 0, "Delay must not be negative");
        SerialScheduledFuture<V> future = new SerialScheduledFuture<>(new FutureTask<V>(vCallable), toNanos(l, timeUnit));
        if (l == 0) {
            future.task.run();
        }
        else {
            tasks.add(future);
        }
        return future;
    }

    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable runnable, long initialDelay, long period, TimeUnit timeUnit)
    {
        requireNonNull(runnable, "Task object is null");
        checkArgument(initialDelay >= 0, "Initial delay must not be negative");
        checkArgument(period > 0, "Repeating delay must be greater than 0");
        SerialScheduledFuture<?> future = new RecurringRunnableSerialScheduledFuture(runnable, toNanos(initialDelay, timeUnit), toNanos(period, timeUnit));
        if (initialDelay == 0) {
            future.task.run();

            if (future.isFailed()) {
                return future;
            }

            future.restartDelayTimer();

        }
        tasks.add(future);
        return future;
    }

    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable runnable, long initialDelay, long period, TimeUnit timeUnit)
    {
        return scheduleAtFixedRate(runnable, initialDelay, period, timeUnit);
    }

    @SuppressFBWarnings(value = "EQ_COMPARETO_USE_OBJECT_EQUALS", justification = "as required by Delayed interface")
    static class SerialScheduledFuture<T>
            implements ScheduledFuture<T>
    {
        long remainingDelayNanos;
        FutureTask<T> task;

        SerialScheduledFuture(FutureTask<T> task, long delayNanos)
        {
            this.task = task;
            this.remainingDelayNanos = delayNanos;
        }

        // wind time off the clock, return the amount of used time in nanos
        long elapseTime(long quantumNanos, @Nullable TestingTicker ticker)
        {
            if (task.isDone() || task.isCancelled()) {
                return 0;
            }

            if (remainingDelayNanos <= quantumNanos) {
                if (ticker != null) {
                    ticker.elapseTime(remainingDelayNanos, NANOSECONDS);
                }
                task.run();
                return remainingDelayNanos;
            }

            remainingDelayNanos -= quantumNanos;
            return quantumNanos;
        }

        public boolean isRecurring()
        {
            return false;
        }

        public void restartDelayTimer()
        {
            throw new UnsupportedOperationException("Can't restart a non-recurring task");
        }

        @Override
        public long getDelay(TimeUnit timeUnit)
        {
            return timeUnit.convert(remainingDelayNanos, NANOSECONDS);
        }

        @Override
        public int compareTo(Delayed delayed)
        {
            if (delayed instanceof SerialScheduledFuture) {
                SerialScheduledFuture other = (SerialScheduledFuture) delayed;
                return Longs.compare(this.remainingDelayNanos, other.remainingDelayNanos);
            }
            return Longs.compare(remainingDelayNanos, delayed.getDelay(NANOSECONDS));
        }

        @Override
        public boolean cancel(boolean b)
        {
            return task.cancel(b);
        }

        @Override
        public boolean isCancelled()
        {
            return task.isCancelled();
        }

        @Override
        public boolean isDone()
        {
            return task.isDone() || task.isCancelled();
        }

        public boolean isFailed()
        {
            if (!isDone()) {
                return false;
            }

            try {
                task.get();
            }
            catch (Throwable ignored) {
                return true;
            }
            return false;
        }

        @Override
        public T get()
                throws InterruptedException, ExecutionException
        {
            if (isCancelled()) {
                throw new CancellationException();
            }

            if (!isDone()) {
                throw new IllegalStateException("Called get() before result was available in SerialScheduledFuture");
            }

            return task.get();
        }

        @Override
        public T get(long l, TimeUnit timeUnit)
                throws InterruptedException, ExecutionException, TimeoutException
        {
            return get();
        }
    }

    static class RecurringRunnableSerialScheduledFuture
            extends SerialScheduledFuture<Void>
    {
        private final long recurringDelayNanos;
        private final Runnable runnable;

        RecurringRunnableSerialScheduledFuture(Runnable runnable, long initialDelayNanos, long recurringDelayNanos)
        {
            super(new FutureTask<Void>(runnable, null), initialDelayNanos);
            this.runnable = runnable;
            this.recurringDelayNanos = recurringDelayNanos;
        }

        @Override
        public boolean isRecurring()
        {
            return true;
        }

        @Override
        public void restartDelayTimer()
        {
            task = new FutureTask<>(runnable, null);
            remainingDelayNanos = recurringDelayNanos;
        }
    }

    private void elapseTime(long quantum, @Nullable TestingTicker ticker)
    {
        List<SerialScheduledFuture<?>> toRequeue = newArrayList();

        // Redirect where the external interface queues up tasks to a temporary
        // collection. This allows the scheduled tasks to schedule future tasks.
        Collection<SerialScheduledFuture<?>> originalTasks = tasks;
        tasks = newArrayList();
        try {
            SerialScheduledFuture<?> current;
            while ((current = futureTasks.poll()) != null) {
                if (current.isCancelled()) {
                    // Drop cancelled tasks
                    continue;
                }

                if (current.isDone()) {
                    throw new AssertionError("Found a done task in the queue (contrary to expectation)");
                }

                // Try to elapse the time quantum off the current item
                long used = current.elapseTime(quantum, ticker);

                // If the item isn't done yet, and didn't fail, we'll need to put it back in the queue
                if (!current.isDone()) {
                    toRequeue.add(current);
                    continue;
                }

                if (used < quantum) {
                    // Partially used the quantum. Elapse the used portion off the rest of the queue so that we can reinsert
                    // this item in its correct spot (if necessary) before continuing with the rest of the quantum. This is
                    // because tasks may execute more than once during a single call to elapse time. We do this recursively
                    // out of convenience. Because this task is the next one that needs to run, all other tasks will need to
                    // run no more than once. When done, any new tasks that were added by the tasks that ran can be added to
                    // the queue for processing.
                    elapseTime(used, (TestingTicker) null);
                    rescheduleTaskIfRequired(futureTasks, current);
                    futureTasks.addAll(tasks);
                    tasks.clear();
                    quantum -= used;
                }
                else {
                    // Completely used the quantum, once we're done with this pass through the queue, may want need to add it back
                    ticker = null;
                    rescheduleTaskIfRequired(toRequeue, current);
                }
            }
            if (ticker != null) {
                ticker.elapseTime(quantum, NANOSECONDS);
            }
        }
        finally {
            futureTasks.addAll(toRequeue);
            futureTasks.addAll(tasks);
            tasks.clear();
            tasks = originalTasks;
        }
    }

    private static void rescheduleTaskIfRequired(Collection<SerialScheduledFuture<?>> tasks, SerialScheduledFuture<?> task)
    {
        if (task.isRecurring() && !task.isFailed()) {
            task.restartDelayTimer();
            tasks.add(task);
        }
    }

    private static long toNanos(long quantum, TimeUnit timeUnit)
    {
        return NANOSECONDS.convert(quantum, timeUnit);
    }
}
