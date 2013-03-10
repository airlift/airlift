package io.airlift.event.client;

import com.google.common.base.Functions;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.AbstractCheckedFuture;
import com.google.common.util.concurrent.CheckedFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import javax.inject.Inject;
import java.util.Set;

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
    public final <T> CheckedFuture<Void, RuntimeException> post(T... event)
            throws IllegalArgumentException
    {
        return post(ImmutableList.copyOf(event));
    }

    @Override
    public <T> CheckedFuture<Void, RuntimeException> post(Iterable<T> events)
            throws IllegalArgumentException
    {
        ImmutableList.Builder<ListenableFuture<Void>> futures = ImmutableList.builder();
        for (EventClient client : clients) {
            futures.add(client.post(events));
        }

        return new ListCheckedFuture(futures.build());
    }

    @Override
    public <T> CheckedFuture<Void, RuntimeException> post(EventGenerator<T> eventGenerator)
            throws IllegalArgumentException
    {
        ImmutableList.Builder<ListenableFuture<Void>> futures = ImmutableList.builder();
        for (EventClient client : clients) {
            futures.add(client.post(eventGenerator));
        }

        return new ListCheckedFuture(futures.build());
    }

    private static class ListCheckedFuture
            extends AbstractCheckedFuture<Void, RuntimeException>
    {
        public ListCheckedFuture(Iterable<? extends ListenableFuture<Void>> futures)
        {
            super(Futures.transform(Futures.allAsList(futures), Functions.<Void>constant(null)));
        }

        @Override
        protected RuntimeException mapException(Exception e)
        {
            throw Throwables.propagate(e);
        }
    }
}
