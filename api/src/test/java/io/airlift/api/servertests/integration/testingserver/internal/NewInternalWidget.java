package io.airlift.api.servertests.integration.testingserver.internal;

import com.google.common.collect.ImmutableMap;

import java.time.Instant;
import java.util.Map;

import static java.util.Objects.requireNonNull;

public record NewInternalWidget(String name, int size, Instant creationDate, Map<String, String> stuff)
{
    public NewInternalWidget
    {
        requireNonNull(name, "name is null");
        requireNonNull(creationDate, "creationDate is null");
        stuff = ImmutableMap.copyOf(stuff);
    }
}
