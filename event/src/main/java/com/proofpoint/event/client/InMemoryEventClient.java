package com.proofpoint.event.client;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Future;

import static com.google.common.collect.Lists.newArrayList;

public class InMemoryEventClient implements EventClient
{
    private final List<Object> events = newArrayList();

    @Override
    public <T> Future<Void> post(T... events)
            throws IllegalArgumentException
    {
        return post(Arrays.asList(events));
    }

    @Override
    public <T> Future<Void> post(Iterable<T> events)
            throws IllegalArgumentException
    {
        Preconditions.checkNotNull(events, "event is null");
        try {
            for (T event : events) {
                Preconditions.checkNotNull(event, "event is null");
                this.events.add(event);
            }
        }
        catch (Exception e) {
            return Futures.immediateFailedFuture(e);
        }
        return Futures.immediateFuture(null);
    }

    @Override
    public <T> Future<Void> post(EventGenerator<T> eventGenerator)
            throws IllegalArgumentException
    {
        Preconditions.checkNotNull(eventGenerator, "eventGenerator is null");
        try {
            eventGenerator.generate(new EventPoster<T>()
            {
                @Override
                public void post(T event)
                {
                    Preconditions.checkNotNull(event, "event is null");
                    InMemoryEventClient.this.events.add(event);
                }
            });
        }
        catch (Exception e) {
            Futures.immediateFailedFuture(e);
        }
        return Futures.immediateFuture(null);
    }

    public List<Object> getEvents()
    {
        return ImmutableList.copyOf(events);
    }
}
