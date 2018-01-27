package io.airlift.concurrent;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.AbstractFuture;
import com.google.common.util.concurrent.ListenableFuture;

import javax.annotation.Nullable;

import java.util.concurrent.ExecutionException;

import static com.google.common.util.concurrent.Futures.getDone;
import static com.google.common.util.concurrent.MoreExecutors.directExecutor;

public final class ExtendedSettableFuture<V>
        extends AbstractFuture<V>
{
    public static <V> ExtendedSettableFuture<V> create()
    {
        return new ExtendedSettableFuture<>();
    }

    private ExtendedSettableFuture() {}

    @Override
    public boolean set(@Nullable V value)
    {
        return super.set(value);
    }

    @Override
    public boolean setException(Throwable throwable)
    {
        return super.setException(throwable);
    }

    /**
     * Sets this current future with the result of the delegate.
     * <p>
     * Values and exceptions are both propagated to this Future.
     * If this Future is cancelled, than the delegate will also be cancelled
     * with the same interrupt flag.
     */
    public void setAsync(ListenableFuture<? extends V> delegate)
    {
        delegate.addListener(() -> {
            if (super.isDone()) {
                // Opportunistically avoid calling getDone. This is critical for the performance
                // of whenAnyCompleteCancelOthers because calling getDone for cancelled Future
                // constructs CancellationException and populates stack trace.
                // See BenchmarkWhenAnyCompleteCancelOthers for benchmark numbers.
                return;
            }

            try {
                set(getDone(delegate));
            }
            catch (ExecutionException e) {
                setException(e.getCause());
            }
            catch (RuntimeException | Error e) {
                setException(e);
            }
        }, directExecutor());

        super.addListener(() -> {
            if (super.isCancelled()) {
                delegate.cancel(super.wasInterrupted());
            }
        }, directExecutor());
    }

    /**
     * @return true if the Future was interrupted when cancelled.
     */
    @VisibleForTesting
    boolean checkWasInterrupted()
    {
        return super.wasInterrupted();
    }
}
