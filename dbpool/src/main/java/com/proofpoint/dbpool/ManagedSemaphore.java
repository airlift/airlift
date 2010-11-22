package com.proofpoint.dbpool;

import org.weakref.jmx.Managed;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class ManagedSemaphore extends Semaphore
{
    private final AtomicLong activePermits = new AtomicLong();
    private int permits;

    public ManagedSemaphore(int permits)
    {
        super(permits);
        this.permits = permits;
    }

    @Managed
    public synchronized int getPermits()
    {
        return permits;
    }

    @Managed
    public synchronized void setPermits(int newPermits)
    {
        int delta = newPermits - this.permits;
        if (delta > 0) {
            // MUST call super since release on this method will modify active permits
            super.release(delta);
        }
        else if (delta < 0) {
            super.reducePermits(-delta);
        }

        this.permits = newPermits;
    }

    @Managed
    public long getActivePermits()
    {
        return activePermits.get();
    }

    @Managed
    public int getAvailablePermits()
    {
        return super.availablePermits();
    }

    @Override
    public int availablePermits()
    {
        return super.availablePermits();
    }

    @Override
    public void acquire()
            throws InterruptedException
    {
        super.acquire();
        activePermits.incrementAndGet();

    }

    @Override
    public void acquireUninterruptibly()
    {
        super.acquireUninterruptibly();
        activePermits.incrementAndGet();
    }

    @Override
    public boolean tryAcquire()
    {
        if (super.tryAcquire()) {
            activePermits.incrementAndGet();
            return true;
        }
        return false;
    }

    @Override
    public boolean tryAcquire(long timeout, TimeUnit unit)
            throws InterruptedException
    {
        if (super.tryAcquire(timeout, unit)) {
            activePermits.incrementAndGet();
            return true;
        }
        return false;
    }

    @Override
    public void acquire(int permits)
            throws InterruptedException
    {
        super.acquire(permits);
        activePermits.addAndGet(permits);
    }

    @Override
    public void acquireUninterruptibly(int permits)
    {
        super.acquireUninterruptibly(permits);
        activePermits.addAndGet(permits);
    }

    @Override
    public boolean tryAcquire(int permits)
    {
        if (super.tryAcquire(permits)) {
            activePermits.addAndGet(permits);
            return true;
        }
        return false;
    }

    @Override
    public boolean tryAcquire(int permits, long timeout, TimeUnit unit)
            throws InterruptedException
    {
        if (super.tryAcquire(permits, timeout, unit)) {
            activePermits.addAndGet(permits);
            return true;
        }
        return false;
    }

    @Override
    public void release()
    {
        super.release();
        activePermits.decrementAndGet();
    }

    @Override
    public void release(int permits)
    {
        super.release(permits);
        activePermits.addAndGet(-permits);
    }

    @Override
    public int drainPermits()
    {
        int permits = super.drainPermits();
        activePermits.addAndGet(permits);
        return permits;
    }
}
