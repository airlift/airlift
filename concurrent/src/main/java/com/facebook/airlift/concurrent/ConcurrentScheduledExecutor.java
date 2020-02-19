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
package com.facebook.airlift.concurrent;

import com.google.common.collect.ImmutableList;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static com.facebook.airlift.concurrent.Threads.daemonThreadsNamed;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.lang.Math.max;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

public class ConcurrentScheduledExecutor
        implements ScheduledExecutorService
{
    private final List<ScheduledExecutorService> schedulers;

    public ConcurrentScheduledExecutor(
            int schedulerCount,
            int threadsPerScheduler,
            String threadBaseName)
    {
        this(schedulerCount, threadsPerScheduler, threadBaseName, false);
    }

    public ConcurrentScheduledExecutor(
            int schedulerCount,
            int threadsPerScheduler,
            String threadBaseName,
            boolean removeOnCancel)
    {
        checkArgument(schedulerCount > 0, "schedulerCount must be at least one");
        checkArgument(threadsPerScheduler > 0, "threadsPerScheduler must be at least one");
        requireNonNull(threadBaseName, "threadBaseName is null");

        ImmutableList.Builder<ScheduledExecutorService> schedulersBuilder = ImmutableList.builder();
        for (int i = 0; i < schedulerCount; i++) {
            ThreadFactory threadFactory = daemonThreadsNamed(threadBaseName + format("-scheduler-%d", i) + "-%s");
            ScheduledThreadPoolExecutor scheduledExecutorService = new ScheduledThreadPoolExecutor(threadsPerScheduler, threadFactory);
            scheduledExecutorService.setRemoveOnCancelPolicy(removeOnCancel);
            schedulersBuilder.add(scheduledExecutorService);
        }
        schedulers = schedulersBuilder.build();
    }

    public static ConcurrentScheduledExecutor createConcurrentScheduledExecutor(
            String threadBaseName,
            int concurrency,
            int totalThreads)
    {
        checkArgument(concurrency >= 1, "concurrency must be at least one");
        int threadsPerScheduler = max(1, totalThreads / concurrency);
        return new ConcurrentScheduledExecutor(concurrency, threadsPerScheduler, threadBaseName);
    }

    @Override
    public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit)
    {
        return getRandomScheduler().schedule(command, delay, unit);
    }

    @Override
    public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit)
    {
        return getRandomScheduler().schedule(callable, delay, unit);
    }

    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit)
    {
        return getRandomScheduler().scheduleAtFixedRate(command, initialDelay, period, unit);
    }

    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit)
    {
        return getRandomScheduler().scheduleWithFixedDelay(command, initialDelay, delay, unit);
    }

    @Override
    public List<Runnable> shutdownNow()
    {
        return schedulers.stream()
                .map(ScheduledExecutorService::shutdownNow)
                .flatMap(list -> list.stream())
                .collect(toImmutableList());
    }

    @Override
    public boolean isShutdown()
    {
        return schedulers.stream().allMatch(ScheduledExecutorService::isShutdown);
    }

    @Override
    public boolean isTerminated()
    {
        return schedulers.stream().allMatch(ScheduledExecutorService::isTerminated);
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit)
            throws InterruptedException
    {
        long startTime = System.currentTimeMillis();
        long timeoutMillis = unit.toMillis(timeout);
        boolean awaitResult = true;

        for (ScheduledExecutorService executor : schedulers) {
            long duration = System.currentTimeMillis() - startTime;
            if (duration < timeoutMillis) {
                awaitResult &= executor.awaitTermination(timeoutMillis - duration, TimeUnit.MILLISECONDS);
            }
            else {
                awaitResult &= executor.isTerminated();
            }
            if (!awaitResult) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void shutdown()
    {
        schedulers.stream().forEach(ScheduledExecutorService::shutdown);
    }

    @Override
    public <T> Future<T> submit(Callable<T> task)
    {
        return getRandomScheduler().submit(task);
    }

    @Override
    public <T> Future<T> submit(Runnable task, T result)
    {
        return getRandomScheduler().submit(task, result);
    }

    @Override
    public Future<?> submit(Runnable task)
    {
        return getRandomScheduler().submit(task);
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks)
            throws InterruptedException
    {
        return getRandomScheduler().invokeAll(tasks);
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
            throws InterruptedException
    {
        return getRandomScheduler().invokeAll(tasks, timeout, unit);
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
            throws InterruptedException, ExecutionException, TimeoutException
    {
        return getRandomScheduler().invokeAny(tasks, timeout, unit);
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks)
            throws InterruptedException, ExecutionException
    {
        return getRandomScheduler().invokeAny(tasks);
    }

    @Override
    public void execute(Runnable command)
    {
        getRandomScheduler().execute(command);
    }

    private ScheduledExecutorService getRandomScheduler()
    {
        return schedulers.get(ThreadLocalRandom.current().nextInt(schedulers.size()));
    }
}
