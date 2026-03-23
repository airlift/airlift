package io.airlift.mcp.storage;

import java.time.Duration;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

public class Signal
{
    private final Lock lock = new ReentrantLock();
    private final Condition condition = lock.newCondition();

    public void signalAll()
    {
        lock.lock();
        try {
            condition.signalAll();
        }
        finally {
            lock.unlock();
        }
    }

    public boolean waitForSignal(Duration timeout)
            throws InterruptedException
    {
        lock.lock();
        try {
            return condition.await(timeout.toMillis(), MILLISECONDS);
        }
        finally {
            lock.unlock();
        }
    }
}
