package io.airlift.stats;

import com.google.common.base.Ticker;

import java.util.concurrent.TimeUnit;

public class TestingTicker
        extends Ticker
{
    private long time;

    public long read()
    {
        return time;
    }

    public void increment(long delta, TimeUnit unit)
    {
        time += unit.toNanos(delta);
    }
}
