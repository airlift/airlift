package io.airlift.concurrent;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import io.airlift.units.Duration;

import javax.annotation.Nullable;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.google.common.base.MoreObjects.firstNonNull;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Throwables.propagateIfPossible;
import static com.google.common.base.Throwables.throwIfInstanceOf;
import static com.google.common.collect.Iterables.isEmpty;
import static com.google.common.util.concurrent.Futures.catchingAsync;
import static com.google.common.util.concurrent.Futures.immediateFailedFuture;
import static com.google.common.util.concurrent.Futures.immediateFuture;
import static com.google.common.util.concurrent.Futures.withTimeout;
import static com.google.common.util.concurrent.MoreExecutors.directExecutor;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

public final class MoreFutures
{
    private MoreFutures() { }

    /**
     * Cancels the destination Future if the source Future is cancelled.
     */
    public static <X, Y> void propagateCancellation(ListenableFuture<? extends X> source, ListenableFuture<? extends Y> destination, boolean mayInterruptIfRunning)
    {
        source.addListener(() -> {
            if (source.isCancelled()) {
                destination.cancel(mayInterruptIfRunning);
            }
        }, directExecutor());
    }

    /**
     * Mirrors all results of the source Future to the destination Future.
     *
     * This also propagates cancellations from the destination Future back to the source Future.
     */
    public static <T> void mirror(ListenableFuture<? extends T> source, SettableFuture<? super T> destination, boolean mayInterruptIfRunning)
    {
        Futures.addCallback(source, new FutureCallback<T>()
        {
            @Override
            public void onSuccess(@Nullable T result)
            {
                destination.set(result);
            }

            @Override
            public void onFailure(Throwable t)
            {
                destination.setException(t);
            }
        });
        propagateCancellation(destination, source, mayInterruptIfRunning);
    }

    /**
     * Attempts to unwrap a throwable that has been wrapped in a {@link CompletionException}.
     */
    public static Throwable unwrapCompletionException(Throwable throwable)
    {
        if (throwable instanceof CompletionException) {
            return firstNonNull(throwable.getCause(), throwable);
        }
        return throwable;
    }

    /**
     * Returns a future that can not be completed or canceled.
     */
    @Deprecated
    public static <V> CompletableFuture<V> unmodifiableFuture(CompletableFuture<V> future)
    {
        return unmodifiableFuture(future, false);
    }

    /**
     * Returns a future that can not be completed or optionally canceled.
     */
    @Deprecated
    public static <V> CompletableFuture<V> unmodifiableFuture(CompletableFuture<V> future, boolean propagateCancel)
    {
        requireNonNull(future, "future is null");

        Function<Boolean, Boolean> onCancelFunction;
        if (propagateCancel) {
            onCancelFunction = future::cancel;
        }
        else {
            onCancelFunction = mayInterrupt -> false;
        }

        UnmodifiableCompletableFuture<V> unmodifiableFuture = new UnmodifiableCompletableFuture<>(onCancelFunction);
        future.whenComplete((value, exception) -> {
            if (exception != null) {
                unmodifiableFuture.internalCompleteExceptionally(exception);
            }
            else {
                unmodifiableFuture.internalComplete(value);
            }
        });
        return unmodifiableFuture;
    }

    /**
     * Returns a failed future containing the specified throwable.
     */
    @Deprecated
    public static <V> CompletableFuture<V> failedFuture(Throwable throwable)
    {
        requireNonNull(throwable, "throwable is null");
        CompletableFuture<V> future = new CompletableFuture<>();
        future.completeExceptionally(throwable);
        return future;
    }

    /**
     * Waits for the value from the future. If the future is failed, the exception
     * is thrown directly if unchecked or wrapped in a RuntimeException. If the
     * thread is interrupted, the thread interruption flag is set and the original
     * InterruptedException is wrapped in a RuntimeException and thrown.
     */
    public static <V> V getFutureValue(Future<V> future)
    {
        return getFutureValue(future, RuntimeException.class);
    }

    /**
     * Waits for the value from the future. If the future is failed, the exception
     * is thrown directly if it is an instance of the specified exception type or
     * unchecked, or it is wrapped in a RuntimeException. If the thread is
     * interrupted, the thread interruption flag is set and the original
     * InterruptedException is wrapped in a RuntimeException and thrown.
     */
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
            propagateIfPossible(cause, exceptionType);
            throw new RuntimeException(cause);
        }
    }

    /**
     * Gets the current value of the future without waiting. If the future
     * value is null, an empty Optional is still returned, and in this case the caller
     * must check the future directly for the null value.
     */
    public static <T> Optional<T> tryGetFutureValue(Future<T> future)
    {
        requireNonNull(future, "future is null");
        if (!future.isDone()) {
            return Optional.empty();
        }

        return tryGetFutureValue(future, 0, MILLISECONDS);
    }

    /**
     * Waits for the the value from the future for the specified time.  If the future
     * value is null, an empty Optional is still returned, and in this case the caller
     * must check the future directly for the null value.  If the future is failed,
     * the exception is thrown directly if unchecked or wrapped in a RuntimeException.
     * If the thread is interrupted, the thread interruption flag is set and the original
     * InterruptedException is wrapped in a RuntimeException and thrown.
     */
    public static <V> Optional<V> tryGetFutureValue(Future<V> future, int timeout, TimeUnit timeUnit)
    {
        return tryGetFutureValue(future, timeout, timeUnit, RuntimeException.class);
    }

    /**
     * Waits for the the value from the future for the specified time.  If the future
     * value is null, an empty Optional is still returned, and in this case the caller
     * must check the future directly for the null value.  If the future is failed,
     * the exception is thrown directly if it is an instance of the specified exception
     * type or unchecked, or it is wrapped in a RuntimeException. If the thread is
     * interrupted, the thread interruption flag is set and the original
     * InterruptedException is wrapped in a RuntimeException and thrown.
     */
    public static <V, E extends Exception> Optional<V> tryGetFutureValue(Future<V> future, int timeout, TimeUnit timeUnit, Class<E> exceptionType)
            throws E
    {
        requireNonNull(future, "future is null");
        checkArgument(timeout >= 0, "timeout is negative");
        requireNonNull(timeUnit, "timeUnit is null");
        requireNonNull(exceptionType, "exceptionType is null");

        try {
            return Optional.ofNullable(future.get(timeout, timeUnit));
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("interrupted", e);
        }
        catch (ExecutionException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            propagateIfPossible(cause, exceptionType);
            throw new RuntimeException(cause);
        }
        catch (TimeoutException expected) {
            // expected
        }
        return Optional.empty();
    }

    /**
     * Creates a future that completes when the first future completes either normally
     * or exceptionally. Cancellation of the future propagates to the supplied futures.
     */
    public static <V> ListenableFuture<V> whenAnyComplete(Iterable<? extends ListenableFuture<? extends V>> futures)
    {
        requireNonNull(futures, "futures is null");
        checkArgument(!isEmpty(futures), "futures is empty");

        ExtendedSettableFuture<V> firstCompletedFuture = ExtendedSettableFuture.create();
        for (ListenableFuture<? extends V> future : futures) {
            firstCompletedFuture.setAsync(future);
        }
        return firstCompletedFuture;
    }

    /**
     * Creates a future that completes when the first future completes either normally
     * or exceptionally. Cancellation of the future does not propagate to the supplied
     * futures.
     */
    @Deprecated
    public static <V> CompletableFuture<V> firstCompletedFuture(Iterable<? extends CompletionStage<? extends V>> futures)
    {
        return firstCompletedFuture(futures, false);
    }

    /**
     * Creates a future that completes when the first future completes either normally
     * or exceptionally. Cancellation of the future will optionally propagate to the
     * supplied futures.
     */
    @Deprecated
    public static <V> CompletableFuture<V> firstCompletedFuture(Iterable<? extends CompletionStage<? extends V>> futures, boolean propagateCancel)
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
        if (propagateCancel) {
            future.exceptionally(throwable -> {
                if (throwable instanceof CancellationException) {
                    for (CompletionStage<? extends V> sourceFuture : futures) {
                        if (sourceFuture instanceof Future) {
                            ((Future<?>) sourceFuture).cancel(true);
                        }
                    }
                }
                return null;
            });
        }
        return future;
    }

    /**
     * Returns an unmodifiable future that is completed when all of the given
     * futures complete. If any of the given futures complete exceptionally, then the
     * returned future also does so immediately, with a CompletionException holding this exception
     * as its cause. Otherwise, the results of the given futures are reflected in the
     * returned future as a list of results matching the input order. If no futures are
     * provided, returns a future completed with an empty list.
     */
    @Deprecated
    public static <V> CompletableFuture<List<V>> allAsList(List<CompletableFuture<? extends V>> futures)
    {
        CompletableFuture<Void> allDoneFuture = CompletableFuture.allOf(futures.toArray(new CompletableFuture[futures.size()]));

        // Eagerly propagate exceptions, rather than waiting for all the futures to complete first (default behavior)
        for (CompletableFuture<? extends V> future : futures) {
            future.whenComplete((v, throwable) -> {
                if (throwable != null) {
                    allDoneFuture.completeExceptionally(throwable);
                }
            });
        }

        return unmodifiableFuture(allDoneFuture.thenApply(v ->
                futures.stream().
                        map(CompletableFuture::join).
                        collect(Collectors.<V>toList())));
    }

    /**
     * Returns a new future that is completed when the supplied future completes or
     * when the timeout expires.  If the timeout occurs or the returned CompletableFuture
     * is canceled, the supplied future will be canceled.
     */
    public static <T> ListenableFuture<T> addTimeout(ListenableFuture<T> future, Callable<T> onTimeout, Duration timeout, ScheduledExecutorService executorService)
    {
        return catchingAsync(withTimeout(future, timeout.toMillis(), MILLISECONDS, executorService), TimeoutException.class, timeoutException -> {
            try {
                return immediateFuture(onTimeout.call());
            }
            catch (Throwable throwable) {
                return immediateFailedFuture(throwable);
            }
        });
    }

    /**
     * Returns a new future that is completed when the supplied future completes or
     * when the timeout expires.  If the timeout occurs or the returned CompletableFuture
     * is canceled, the supplied future will be canceled.
     */
    @Deprecated
    public static <T> CompletableFuture<T> addTimeout(CompletableFuture<T> future, Callable<T> onTimeout, Duration timeout, ScheduledExecutorService executorService)
    {
        requireNonNull(future, "future is null");
        requireNonNull(onTimeout, "timeoutValue is null");
        requireNonNull(timeout, "timeout is null");
        requireNonNull(executorService, "executorService is null");

        // if the future is already complete, just return it
        if (future.isDone()) {
            return future;
        }

        // create an unmodifiable future that propagates cancel
        // down cast is safe because this is our code
        UnmodifiableCompletableFuture<T> futureWithTimeout = (UnmodifiableCompletableFuture<T>) unmodifiableFuture(future, true);

        // schedule a task to complete the future when the time expires
        ScheduledFuture<?> timeoutTaskFuture = executorService.schedule(new TimeoutFutureTask<>(futureWithTimeout, onTimeout, future), timeout.toMillis(), MILLISECONDS);

        // when future completes, cancel the timeout task
        future.whenCompleteAsync((value, exception) -> timeoutTaskFuture.cancel(false), executorService);

        return futureWithTimeout;
    }

    /**
     * Converts a ListenableFuture to a CompletableFuture. Cancellation of the
     * CompletableFuture will be propagated to the ListenableFuture.
     */
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

    /**
     * Converts a CompletableFuture to a ListenableFuture. Cancellation of the
     * ListenableFuture will be propagated to the CompletableFuture.
     */
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
            public void onFailure(Throwable throwable)
            {
                if (throwable instanceof CancellationException) {
                    completableFuture.cancel(true);
                }
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
    
    public static <T> void addExceptionCallback(ListenableFuture<T> result, Consumer<Throwable> exceptionCallback)
    {
        Futures.addCallback(result, new FutureCallback<T>() {
            @Override
            public void onSuccess(@Nullable T result)
            {
            }

            @Override
            public void onFailure(Throwable t)
            {
                exceptionCallback.accept(t);
            }
        });
    }

    public static <T> void addExceptionCallback(ListenableFuture<T> result, Runnable exceptionCallback)
    {
        Futures.addCallback(result, new FutureCallback<T>() {
            @Override
            public void onSuccess(@Nullable T result)
            {
            }

            @Override
            public void onFailure(Throwable t)
            {
                exceptionCallback.run();
            }
        });
    }

    private static class UnmodifiableCompletableFuture<V>
            extends CompletableFuture<V>
    {
        private final Function<Boolean, Boolean> onCancel;

        public UnmodifiableCompletableFuture(Function<Boolean, Boolean> onCancel)
        {
            this.onCancel = requireNonNull(onCancel, "onCancel is null");
        }

        void internalComplete(V value)
        {
            super.complete(value);
        }

        void internalCompleteExceptionally(Throwable ex)
        {
            super.completeExceptionally(ex);
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning)
        {
            return onCancel.apply(mayInterruptIfRunning);
        }

        @Override
        public boolean complete(V value)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean completeExceptionally(Throwable ex)
        {
            if (ex instanceof CancellationException) {
                return cancel(false);
            }
            throw new UnsupportedOperationException();
        }

        @Override
        public void obtrudeValue(V value)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void obtrudeException(Throwable ex)
        {
            throw new UnsupportedOperationException();
        }
    }

    private static class TimeoutFutureTask<T>
            implements Runnable
    {
        private final UnmodifiableCompletableFuture<T> settableFuture;
        private final Callable<T> timeoutValue;
        private final WeakReference<CompletableFuture<T>> futureReference;

        public TimeoutFutureTask(UnmodifiableCompletableFuture<T> settableFuture, Callable<T> timeoutValue, CompletableFuture<T> future)
        {
            this.settableFuture = settableFuture;
            this.timeoutValue = timeoutValue;

            // the scheduled executor can hold on to the timeout task for a long time, and
            // the future can reference large expensive objects.  Since we are only interested
            // in canceling this future on a timeout, only hold a weak reference to the future
            this.futureReference = new WeakReference<>(future);
        }

        @Override
        public void run()
        {
            if (settableFuture.isDone()) {
                return;
            }

            // run the timeout task and set the result into the future
            try {
                T result = timeoutValue.call();
                settableFuture.internalComplete(result);
            }
            catch (Throwable t) {
                settableFuture.internalCompleteExceptionally(t);
                throwIfInstanceOf(t, RuntimeException.class);
            }

            // cancel the original future, if it still exists
            Future<T> future = futureReference.get();
            if (future != null) {
                future.cancel(true);
            }
        }
    }
}
