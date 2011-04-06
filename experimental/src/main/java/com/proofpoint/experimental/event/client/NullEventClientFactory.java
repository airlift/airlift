package com.proofpoint.experimental.event.client;

import com.google.common.base.Preconditions;

import java.util.List;

public class NullEventClientFactory implements EventClientFactory
{
    @Override
    public <T> NullEventClient<T> createEventClient(List<EventTypeMetadata<? extends T>> eventTypes)
    {
        Preconditions.checkNotNull(eventTypes, "eventTypes is null");
        return new NullEventClient<T>();
    }
}
