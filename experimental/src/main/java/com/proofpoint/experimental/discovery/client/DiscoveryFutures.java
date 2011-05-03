package com.proofpoint.experimental.discovery.client;

import com.google.common.base.Function;
import com.google.common.util.concurrent.CheckedFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;

class DiscoveryFutures
{
    static <T> CheckedFuture<T, DiscoveryException> toDiscoveryFuture(final String name, ListenableFuture<T> future)
    {
        return Futures.makeChecked(future, new Function<Exception, DiscoveryException>()
        {
            @Override
            public DiscoveryException apply(Exception e)
            {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                    return new DiscoveryException(name + " was interrupted");
                }
                if (e instanceof CancellationException) {
                    return new DiscoveryException(name + " was canceled");
                }

                Throwable cause = e;
                if (e instanceof ExecutionException) {
                    if (e.getCause() != null) {
                        cause = e.getCause();
                    }
                }

                if (cause instanceof DiscoveryException) {
                    return (DiscoveryException) cause;
                }

                return new DiscoveryException(name + " failed", cause);
            }
        });
    }

}
