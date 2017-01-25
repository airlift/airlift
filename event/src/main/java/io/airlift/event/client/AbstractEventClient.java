package io.airlift.event.client;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.IOException;
import java.util.Arrays;

import static java.util.Objects.requireNonNull;

public abstract class AbstractEventClient
        implements EventClient
{
    @SafeVarargs
    @Override
    public final <T> ListenableFuture<Void> post(T... event)
            throws IllegalArgumentException
    {
        requireNonNull(event, "event is null");
        return post(Arrays.asList(event));
    }

    @Override
    public final <T> ListenableFuture<Void> post(final Iterable<T> events)
            throws IllegalArgumentException
    {
        requireNonNull(events, "events is null");
        return post(new EventGenerator<T>()
        {
            @Override
            public void generate(EventPoster<T> eventPoster)
                    throws IOException
            {
                for (T event : events) {
                    requireNonNull(event, "event is null");
                    eventPoster.post(event);
                }
            }
        });
    }

    @Override
    public final <T> ListenableFuture<Void> post(EventGenerator<T> eventGenerator)
            throws IllegalArgumentException
    {
        try {
            eventGenerator.generate(new EventPoster<T>()
            {
                @Override
                public void post(T event)
                        throws IOException
                {
                    requireNonNull(event, "event is null");
                    postEvent(event);
                }
            });
        }
        catch (IOException e) {
            return Futures.immediateFailedCheckedFuture(e);
        }
        return Futures.immediateCheckedFuture(null);
    }

    protected abstract <T> void postEvent(T event)
            throws IOException;
}
