package com.proofpoint.experimental.event.client;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import com.google.inject.Provider;

import java.util.List;

public class EventClientProvider<T> implements Provider<EventClient<T>>
{
    private final List<EventTypeMetadata<? extends T>> types;
    private EventClientFactory eventClientFactory;

    public EventClientProvider(List<EventTypeMetadata<? extends T>> eventTypes)
    {
        Preconditions.checkNotNull(eventTypes, "eventTypes is null");
        this.types = eventTypes;
    }

    @Inject
    public void setEventClientFactory(EventClientFactory eventClientFactory)
    {
        this.eventClientFactory = eventClientFactory;
    }

    @Override
    public EventClient<T> get()
    {
        Preconditions.checkNotNull(eventClientFactory, "eventClientFactory is null");
        return eventClientFactory.createEventClient(types);
    }
}
