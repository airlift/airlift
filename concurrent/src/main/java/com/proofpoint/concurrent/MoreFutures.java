package com.proofpoint.concurrent;

import com.google.common.base.Throwables;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;

import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.Iterables.isEmpty;
import static java.util.Objects.requireNonNull;

public final class MoreFutures
{
    private MoreFutures() { }

    public static <V> CompletableFuture<V> failedFuture(Throwable throwable)
    {
        requireNonNull(throwable, "throwable is null");
        CompletableFuture<V> future = new CompletableFuture<>();
        future.completeExceptionally(throwable);
        return future;
    }

    public static <V> V getFutureValue(Future<V> future)
    {
        return getFutureValue(future, RuntimeException.class);
    }

    public static <V, E extends Exception> V getFutureValue(Future<V> future, Class<E> exceptionType)
            throws E
    {
        requireNonNull(future, "future is null");
        requireNonNull(exceptionType, "exceptionType is null");

        try {
            return future.get();
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("interrupted", e);
        }
        catch (ExecutionException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            Throwables.propagateIfInstanceOf(cause, exceptionType);
            throw Throwables.propagate(cause);
        }
    }

    public static <V> Optional<V> tryGetFutureValue(Future<V> future, int timeout, TimeUnit timeUnit)
    {
        return tryGetFutureValue(future, timeout, timeUnit, RuntimeException.class);
    }

    public static <V, E extends Exception> Optional<V> tryGetFutureValue(Future<V> future, int timeout, TimeUnit timeUnit, Class<E> exceptionType)
            throws E
    {
        requireNonNull(future, "future is null");
        checkArgument(timeout >= 0, "timeout is negative");
        requireNonNull(timeUnit, "timeUnit is null");
        requireNonNull(exceptionType, "exceptionType is null");

        try {
            return Optional.of(future.get(timeout, timeUnit));
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("interrupted", e);
        }
        catch (ExecutionException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            Throwables.propagateIfInstanceOf(cause, exceptionType);
            throw Throwables.propagate(cause);
        }
        catch (TimeoutException expected) {
            // expected
        }
        return Optional.empty();
    }

    public static <V> CompletableFuture<V> firstCompletedFuture(Iterable<? extends CompletionStage<? extends V>> futures)
    {
        requireNonNull(futures, "futures is null");
        checkArgument(!isEmpty(futures), "futures is empty");

        CompletableFuture<V> future = new CompletableFuture<>();
        for (CompletionStage<? extends V> stage : futures) {
            stage.whenComplete((value, exception) -> {
                if (exception != null) {
                    future.completeExceptionally(exception);
                }
                else {
                    future.complete(value);
                }
            });
        }
        return future;
    }

    public static <V> CompletableFuture<V> toCompletableFuture(ListenableFuture<V> listenableFuture)
    {
        requireNonNull(listenableFuture, "listenableFuture is null");

        CompletableFuture<V> future = new CompletableFuture<>();
        future.exceptionally(throwable -> {
            if (throwable instanceof CancellationException) {
                listenableFuture.cancel(true);
            }
            return null;
        });

        Futures.addCallback(listenableFuture, new FutureCallback<V>()
        {
            @Override
            public void onSuccess(V result)
            {
                future.complete(result);
            }

            @Override
            public void onFailure(Throwable t)
            {
                future.completeExceptionally(t);
            }
        });
        return future;
    }

    public static <V> ListenableFuture<V> toListenableFuture(CompletableFuture<V> completableFuture)
    {
        requireNonNull(completableFuture, "completableFuture is null");
        SettableFuture<V> future = SettableFuture.create();
        Futures.addCallback(future, new FutureCallback<V>()
        {
            @Override
            public void onSuccess(V result)
            {
            }

            @Override
            public void onFailure(Throwable t)
            {
                completableFuture.completeExceptionally(t);
            }
        });

        completableFuture.whenComplete((value, exception) -> {
            if (exception != null) {
                future.setException(exception);
            }
            else {
                future.set(value);
            }
        });
        return future;
    }
}
