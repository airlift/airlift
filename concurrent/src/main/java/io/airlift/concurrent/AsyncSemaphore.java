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

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;

import javax.annotation.concurrent.ThreadSafe;

import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Verify.verify;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.util.concurrent.Futures.immediateCancelledFuture;
import static com.google.common.util.concurrent.Futures.immediateFailedFuture;
import static com.google.common.util.concurrent.MoreExecutors.directExecutor;
import static io.airlift.concurrent.MoreFutures.allAsListWithCancellationOnFailure;
import static java.util.Objects.requireNonNull;

/**
 * Guarantees that no more than maxPermits of tasks will be run concurrently.
 * The class will rely on the ListenableFuture returned by the submitter function to determine
 * when a task has been completed. The submitter function NEEDS to be thread-safe and is recommended
 * to do the bulk of its work asynchronously.
 */
@ThreadSafe
public class AsyncSemaphore<T, R>
{
    private final Queue<QueuedTask<T, R>> queuedTasks = new ConcurrentLinkedQueue<>();
    private final AtomicInteger counter = new AtomicInteger();
    private final Runnable runNextTask = this::runNext;
    private final int maxPermits;
    private final Executor submitExecutor;
    private final Function<T, ListenableFuture<R>> submitter;

    /**
     * Process a list of tasks as a single unit
     * (similar to {@link com.google.common.util.concurrent.Futures#allAsList(ListenableFuture[])})
     * with limiting the number of tasks running in parallel.
     * <p>
     * This method may be useful for limiting the number of concurrent requests sent to a remote server when
     * trying to load multiple related entities concurrently:
     * <p>
     * For example:
     * <pre>{@code
     * List<Integer> userIds = Lists.of(1, 2, 3);
     * ListenableFuture<List<UserInfo>> future = processAll(ids, client::getUserInfoById, 2, executor);
     * List<UserInfo> userInfos = future.get(...);
     * }</pre>
     *
     * @param tasks tasks to process
     * @param submitter task submitter
     * @param maxConcurrency maximum number of tasks allowed to run in parallel
     * @param submitExecutor task submission executor
     * @return {@link ListenableFuture} containing a list of values returned by the {@code tasks}.
     * The order of elements in the list matches the order of {@code tasks}.
     * If the result future is cancelled all the remaining tasks are cancelled (submitted tasks will be cancelled, pending tasks will not be submitted).
     * If any of the submitted tasks fails or are cancelled, the result future is too.
     * If any of the submitted tasks fails or are cancelled, the remaining tasks are cancelled.
     */
    public static <T, R> ListenableFuture<List<R>> processAll(List<T> tasks, Function<T, ListenableFuture<R>> submitter, int maxConcurrency, Executor submitExecutor)
    {
        SettableFuture<List<R>> resultFuture = SettableFuture.create();
        AsyncSemaphore<T, R> semaphore = new AsyncSemaphore<>(maxConcurrency, submitExecutor, task -> {
            if (resultFuture.isCancelled()) {
                // Task cancellation tends to happen in task submission order, which can race with subsequent task submissions after previous cancellations.
                // This eager check prevents this race from occurring, and can reduce the number of unnecessary submissions.
                return immediateCancelledFuture();
            }
            return submitter.apply(task);
        });
        resultFuture.setFuture(allAsListWithCancellationOnFailure(tasks.stream()
                .map(semaphore::submit)
                .collect(toImmutableList())));
        return resultFuture;
    }

    public AsyncSemaphore(int maxPermits, Executor submitExecutor, Function<T, ListenableFuture<R>> submitter)
    {
        checkArgument(maxPermits > 0, "must have at least one permit");
        this.maxPermits = maxPermits;
        this.submitExecutor = requireNonNull(submitExecutor, "submitExecutor is null");
        this.submitter = requireNonNull(submitter, "submitter is null");
    }

    public ListenableFuture<R> submit(T task)
    {
        QueuedTask<T, R> queuedTask = new QueuedTask<>(task);
        queuedTasks.add(queuedTask);
        acquirePermit();
        return queuedTask.getCompletionFuture();
    }

    private void acquirePermit()
    {
        if (counter.incrementAndGet() <= maxPermits) {
            // Kick off a task if not all permits have been handed out
            submitExecutor.execute(runNextTask);
        }
    }

    private void releasePermit()
    {
        if (counter.getAndDecrement() > maxPermits) {
            // Now that a task has finished, we can kick off another task if there are more tasks than permits
            submitExecutor.execute(runNextTask);
        }
    }

    private void runNext()
    {
        QueuedTask<T, R> queuedTask = queuedTasks.poll();
        verify(queuedTask != null);
        if (!queuedTask.getCompletionFuture().isDone()) {
            queuedTask.setFuture(submitTask(queuedTask.getTask()));
        }
        queuedTask.getCompletionFuture().addListener(this::releasePermit, directExecutor());
    }

    private ListenableFuture<R> submitTask(T task)
    {
        try {
            ListenableFuture<R> future = submitter.apply(task);
            if (future == null) {
                return immediateFailedFuture(new NullPointerException("Submitter returned a null future for task: " + task));
            }
            return future;
        }
        catch (Exception e) {
            return immediateFailedFuture(e);
        }
    }

    private static class QueuedTask<T, R>
    {
        private final T task;
        private final SettableFuture<R> settableFuture = SettableFuture.create();

        private QueuedTask(T task)
        {
            this.task = requireNonNull(task, "task is null");
        }

        public T getTask()
        {
            return task;
        }

        public void setFuture(ListenableFuture<R> future)
        {
            settableFuture.setFuture(future);
        }

        public ListenableFuture<R> getCompletionFuture()
        {
            return settableFuture;
        }
    }
}
