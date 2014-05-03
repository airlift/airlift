package io.airlift.concurrent;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Converts an Executor into a minimalistic ExecutorService
 */
public class ExecutorServiceAdapter
        implements ExecutorService
{
    private final Executor executor;

    public ExecutorServiceAdapter(Executor executor)
    {
        this.executor = checkNotNull(executor, "executor is null");
    }

    public static ExecutorService from(Executor executor)
    {
        return new ExecutorServiceAdapter(executor);
    }

    @Override
    public void execute(Runnable command)
    {
        executor.execute(command);
    }

    @Override
    public <T> Future<T> submit(Callable<T> task)
    {
        FutureTask<T> futureTask = new FutureTask<>(task);
        execute(futureTask);
        return futureTask;
    }

    @Override
    public <T> Future<T> submit(Runnable task, T result)
    {
        return submit(Executors.callable(task, result));
    }

    @Override
    public Future<?> submit(Runnable task)
    {
        return submit(Executors.callable(task));
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks)
            throws InterruptedException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
            throws InterruptedException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks)
            throws InterruptedException, ExecutionException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
            throws InterruptedException, ExecutionException, TimeoutException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void shutdown()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<Runnable> shutdownNow()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isShutdown()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isTerminated()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit)
            throws InterruptedException
    {
        throw new UnsupportedOperationException();
    }
}
