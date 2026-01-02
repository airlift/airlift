package io.airlift.api.servertests.integration.testingserver.internal;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

public class InternalWidgetId
        implements Comparable<InternalWidgetId>
{
    private static final AtomicInteger nextId = new AtomicInteger(1000);

    private final int id;

    public InternalWidgetId()
    {
        this(nextId.getAndIncrement());
    }

    public InternalWidgetId(int id)
    {
        this.id = id;
    }

    @JsonCreator
    public InternalWidgetId(String id)
    {
        this(Integer.parseInt(id));
    }

    public int id()
    {
        return id;
    }

    @Override
    public int compareTo(InternalWidgetId rhs)
    {
        return Integer.compare(id, rhs.id);
    }

    @Override
    public boolean equals(Object o)
    {
        return o instanceof InternalWidgetId internalWidgetId && id == internalWidgetId.id;
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(id);
    }

    @Override
    public String toString()
    {
        return Integer.toString(id);
    }
}
