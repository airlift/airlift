package io.airlift.http.client.netty;

import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;

import javax.annotation.concurrent.GuardedBy;
import java.util.LinkedList;
import java.util.Queue;

public class PermitQueue
{
    private final int maxPermits;

    @GuardedBy("this")
    private final Queue<SettableFuture<?>> pending = new LinkedList<>();

    @GuardedBy("this")
    private int permits;

    public PermitQueue(int maxPermits)
    {
        this.maxPermits = maxPermits;
    }

    public ListenableFuture<?> acquire()
    {
        // todo what happens if I cancel this future
        SettableFuture<?> future = SettableFuture.create();

        boolean acquired = false;

        synchronized (this) {
            if (permits < maxPermits) {
                permits++;
                acquired = true;
            }
            else {
                pending.add(future);
            }
        }

        if (acquired) {
            future.set(null);
        }

        return future;
    }

    public void release()
    {
        SettableFuture<?> future = null;
        boolean acquired = false;

        synchronized (this) {
            Preconditions.checkState(permits >= 0, "Used permits is already 0");
            permits--;

            if (permits < maxPermits) {
                future = pending.poll();

                if (future != null) {
                    permits++;
                    acquired = true;
                }
            }
        }

        if (acquired) {
            future.set(null);
        }
    }
}
