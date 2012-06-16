package io.airlift.event.client;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.CheckedFuture;
import com.google.common.util.concurrent.Futures;

import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.List;

import static com.google.common.collect.Lists.newArrayList;

public class InMemoryEventClient implements EventClient
{
    private final List<Object> events = newArrayList();

    @Override
    public <T> CheckedFuture<Void, RuntimeException> post(T... events)
            throws IllegalArgumentException
    {
        return post(Arrays.asList(events));
    }

    @Override
    public <T> CheckedFuture<Void, RuntimeException> post(Iterable<T> events)
            throws IllegalArgumentException
    {
        Preconditions.checkNotNull(events, "event is null");
        for (T event : events) {
            Preconditions.checkNotNull(event, "event is null");
            this.events.add(event);
        }
        return Futures.immediateCheckedFuture(null);
    }

    @Override
    public <T> CheckedFuture<Void, RuntimeException> post(EventGenerator<T> eventGenerator)
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
        catch (IOException e) {
            return Futures.<Void, RuntimeException>immediateFailedCheckedFuture(new EventSubmissionFailedException("event", "general", ImmutableMap.of(URI.create("in-memory://"), e)));
        }
        return Futures.immediateCheckedFuture(null);
    }

    public List<Object> getEvents()
    {
        return ImmutableList.copyOf(events);
    }
}
