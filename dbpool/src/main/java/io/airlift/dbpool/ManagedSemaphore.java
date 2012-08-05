/*
 * Copyright 2010 Proofpoint, Inc.
 *
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
package io.airlift.dbpool;

import org.weakref.jmx.Managed;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

class ManagedSemaphore extends Semaphore
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
