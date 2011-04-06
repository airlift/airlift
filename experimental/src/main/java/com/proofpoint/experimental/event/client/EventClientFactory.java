package com.proofpoint.experimental.event.client;

import java.util.List;

public interface EventClientFactory
{
    <T> EventClient<T> createEventClient(List<EventTypeMetadata<? extends T>> types);
}
