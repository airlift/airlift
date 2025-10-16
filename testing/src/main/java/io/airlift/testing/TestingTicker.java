package io.airlift.testing;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.base.Ticker;
import com.google.errorprone.annotations.ThreadSafe;
import java.util.concurrent.TimeUnit;

@ThreadSafe
public class TestingTicker extends Ticker {
    private volatile long time;

    @Override
    public long read() {
        return time;
    }

    public synchronized void increment(long delta, TimeUnit unit) {
        checkArgument(delta >= 0, "delta is negative");
        time += unit.toNanos(delta);
    }
}
