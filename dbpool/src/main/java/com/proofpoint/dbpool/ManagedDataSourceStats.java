package com.proofpoint.dbpool;

import org.weakref.jmx.Managed;
import org.weakref.jmx.Nested;

import java.util.concurrent.atomic.AtomicLong;

public final class ManagedDataSourceStats
{
    private final TimedStat checkout = new TimedStat();
    private final TimedStat create = new TimedStat();
    private final TimedStat held = new TimedStat();
    private final AtomicLong connectionErrorCount = new AtomicLong();

    @Managed
    @Nested
    public TimedStat getCheckout()
    {
        return checkout;
    }

    @Managed
    @Nested
    public TimedStat getCreate()
    {
        return create;
    }

    @Managed
    @Nested
    public TimedStat getHeld()
    {
        return held;
    }

    @Managed
    public long getConnectionErrorCount()
    {
        return connectionErrorCount.get();
    }

    void connectionCheckedOut(Duration elapsedTime)
    {
        checkout.addValue(elapsedTime);
    }

    void connectionCreated(Duration elapsedTime)
    {
        create.addValue(elapsedTime);
    }

    void connectionReturned(Duration elapsedTime)
    {
        held.addValue(elapsedTime);
    }

    void connectionErrorOccurred()
    {
        connectionErrorCount.incrementAndGet();
    }
}
