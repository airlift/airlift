package io.airlift.event.client;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.inject.Inject;

import java.util.Set;

import static com.google.common.util.concurrent.MoreExecutors.directExecutor;

public class MultiEventClient
        implements EventClient
{
    private final Set<EventClient> clients;

    public MultiEventClient(EventClient... clients)
    {
        this(ImmutableSet.copyOf(clients));
    }

    @Inject
    public MultiEventClient(Set<EventClient> clients)
    {
        this.clients = clients;
    }

    @SafeVarargs
    @Override
    public final <T> ListenableFuture<Void> post(T... event)
            throws IllegalArgumentException
    {
        return post(ImmutableList.copyOf(event));
    }

    @Override
    public <T> ListenableFuture<Void> post(Iterable<T> events)
            throws IllegalArgumentException
    {
        ImmutableList.Builder<ListenableFuture<Void>> futures = ImmutableList.builder();
        for (EventClient client : clients) {
            futures.add(client.post(events));
        }

        return Futures.transform(Futures.allAsList(futures.build()), x -> null, directExecutor());
    }

    @Override
    public <T> ListenableFuture<Void> post(EventGenerator<T> eventGenerator)
            throws IllegalArgumentException
    {
        ImmutableList.Builder<ListenableFuture<Void>> futures = ImmutableList.builder();
        for (EventClient client : clients) {
            futures.add(client.post(eventGenerator));
        }

        return Futures.transform(Futures.allAsList(futures.build()), x -> null, directExecutor());
    }
}
