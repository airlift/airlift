package com.proofpoint.experimental.event.client;

import org.codehaus.jackson.map.JsonSerializer;

public class EventJsonSerializer
{
    public static <T> JsonSerializer<T> createEventJsonSerializer(EventTypeMetadata<T> eventTypeMetadata, int version)
    {
        switch (version) {
            case 1:
                return EventJsonSerializerV1.createEventJsonSerializer(eventTypeMetadata);

            case 2:
                return EventJsonSerializerV2.createEventJsonSerializer(eventTypeMetadata);

            default:
                throw new RuntimeException(String.format("EventJsonSerializer version %d is unknown", version));
        }
    }
}
