package io.airlift.testing;

import com.google.common.base.Ticker;
import com.google.errorprone.annotations.ThreadSafe;

import java.util.concurrent.TimeUnit;

import static com.google.common.base.Preconditions.checkArgument;

@ThreadSafe
public class TestingTicker
        extends Ticker
{
    private volatile long time;

    @Override
    public long read()
    {
        return time;
    }

    public synchronized void increment(long delta, TimeUnit unit)
    {
        checkArgument(delta >= 0, "delta is negative");
        time += unit.toNanos(delta);
    }
}
