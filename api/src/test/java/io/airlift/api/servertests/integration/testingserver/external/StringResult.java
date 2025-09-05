package io.airlift.api.servertests.integration.testingserver.external;

import io.airlift.api.ApiResource;

import static java.util.Objects.requireNonNull;

@ApiResource(name = "test", description = "Used to return strings from test methods")
public record StringResult(String message)
{
    public StringResult
    {
        requireNonNull(message, "message is null");
    }
}
