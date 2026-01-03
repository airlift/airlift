package io.airlift.mcp.sessions;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

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

    public boolean waitForSignal(long timeout, TimeUnit unit)
            throws InterruptedException
    {
        lock.lock();
        try {
            return condition.await(timeout, unit);
        }
        finally {
            lock.unlock();
        }
    }
}
