package com.proofpoint.experimental.event.client;

import com.google.common.base.Preconditions;

import java.util.List;

public class InMemoryEventClientFactory implements EventClientFactory
{
    @Override
    public InMemoryEventClient createEventClient(List<EventTypeMetadata<?>> eventTypes)
    {
        Preconditions.checkNotNull(eventTypes, "eventTypes is null");
        return new InMemoryEventClient();
    }
}
