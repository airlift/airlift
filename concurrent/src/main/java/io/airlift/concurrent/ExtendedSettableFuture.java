package io.airlift.concurrent;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.AbstractFuture;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import javax.annotation.Nullable;

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
        Futures.addCallback(delegate, new FutureCallback<V>()
        {
            @Override
            public void onSuccess(@Nullable V result)
            {
                set(result);
            }

            @Override
            public void onFailure(Throwable t)
            {
                setException(t);
            }
        });

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
