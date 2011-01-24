package com.proofpoint.platform.sample;

import org.weakref.jmx.Managed;

import java.util.concurrent.atomic.AtomicLong;

public class PersonStoreStats
{
    private final AtomicLong fetched = new AtomicLong();
    private final AtomicLong added = new AtomicLong();
    private final AtomicLong updated = new AtomicLong();
    private final AtomicLong removed = new AtomicLong();

    @Managed
    public long getFetched()
    {
        return fetched.get();
    }

    @Managed
    public long getAdded()
    {
        return added.get();
    }

    @Managed
    public long getUpdated()
    {
        return updated.get();
    }

    @Managed
    public long getRemoved()
    {
        return removed.get();
    }

    public void personFetched()
    {
        fetched.getAndIncrement();
    }

    public void personAdded()
    {
        added.getAndIncrement();
    }

    public void personUpdated()
    {
        updated.getAndIncrement();
    }

    public void personRemoved()
    {
        removed.getAndIncrement();
    }
}
