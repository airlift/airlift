package com.proofpoint.event.client;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.CheckedFuture;
import com.google.common.util.concurrent.Futures;

import java.net.URI;
import java.util.Arrays;

public class NullEventClient implements EventClient
{
    @Override
    public <T> CheckedFuture<Void, ? extends RuntimeException> post(T... events)
            throws IllegalArgumentException
    {
        Preconditions.checkNotNull(events, "event is null");
        return post(Arrays.asList(events));
    }

    @Override
    public <T> CheckedFuture<Void, ? extends RuntimeException> post(Iterable<T> events)
            throws IllegalArgumentException
    {
        Preconditions.checkNotNull(events, "event is null");
        for (T event : events) {
            Preconditions.checkNotNull(event, "event is null");
        }
        return Futures.immediateCheckedFuture(null);
    }

    @Override
    public <T> CheckedFuture<Void, ? extends RuntimeException> post(EventGenerator<T> eventGenerator)
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
                }
            });
        }
        catch (Exception e) {
            return Futures.immediateFailedCheckedFuture(new EventSubmissionFailedException("event", "general", ImmutableMap.of(URI.create("null://"), e)));
        }
        return Futures.immediateCheckedFuture(null);
    }
}
