package io.airlift.event.client;

import com.google.common.util.concurrent.ListenableFuture;

import java.io.IOException;
import java.util.Arrays;

import static com.google.common.util.concurrent.Futures.immediateFailedFuture;
import static com.google.common.util.concurrent.Futures.immediateFuture;
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
        return post((EventGenerator<T>) eventPoster -> {
            for (T event : events) {
                requireNonNull(event, "event is null");
                eventPoster.post(event);
            }
        });
    }

    @Override
    public final <T> ListenableFuture<Void> post(EventGenerator<T> eventGenerator)
            throws IllegalArgumentException
    {
        try {
            eventGenerator.generate(event -> {
                requireNonNull(event, "event is null");
                postEvent(event);
            });
        }
        catch (IOException e) {
            return immediateFailedFuture(e);
        }
        return immediateFuture(null);
    }

    protected abstract <T> void postEvent(T event)
            throws IOException;
}
