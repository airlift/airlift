package com.proofpoint.experimental.event.client;

import java.util.List;

public interface EventClientFactory
{
    EventClient createEventClient(List<EventTypeMetadata<?>> types);
}
