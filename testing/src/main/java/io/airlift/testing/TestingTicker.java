package io.airlift.testing;

import com.google.common.base.Ticker;

import java.util.concurrent.TimeUnit;

import static com.google.common.base.Preconditions.checkArgument;

public class TestingTicker
        extends Ticker
{
    private long time;

    @Override
    public long read()
    {
        return time;
    }

    public void increment(long delta, TimeUnit unit)
    {
        checkArgument(delta >= 0, "delta is negative");
        time += unit.toNanos(delta);
    }
}
