package io.airlift.jfr;

import java.util.Map;

import static java.util.Objects.requireNonNull;

record FlightRecorderEvent(String eventName, Map<String, String> properties)
{
    public FlightRecorderEvent
    {
        requireNonNull(eventName, "eventName is null");
    }
}
