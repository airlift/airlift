package com.proofpoint.experimental.event.client;

import com.google.common.base.Preconditions;

import java.util.List;

public class InMemoryEventClientFactory implements EventClientFactory
{
    @Override
    public <T> InMemoryEventClient<T> createEventClient(List<EventTypeMetadata<? extends T>> eventTypes)
    {
        Preconditions.checkNotNull(eventTypes, "eventTypes is null");
        return new InMemoryEventClient<T>();
    }
}
