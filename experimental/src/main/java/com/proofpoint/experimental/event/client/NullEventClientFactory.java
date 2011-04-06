package com.proofpoint.experimental.event.client;

import com.google.common.base.Preconditions;

import java.util.List;

public class NullEventClientFactory implements EventClientFactory
{
    @Override
    public NullEventClient createEventClient(List<EventTypeMetadata<?>> eventTypes)
    {
        Preconditions.checkNotNull(eventTypes, "eventTypes is null");
        return new NullEventClient();
    }
}
