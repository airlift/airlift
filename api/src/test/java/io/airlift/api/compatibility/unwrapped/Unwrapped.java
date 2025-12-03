package io.airlift.api.compatibility.unwrapped;

import io.airlift.api.ApiDescription;
import io.airlift.api.ApiResource;
import io.airlift.api.ApiUnwrapped;

import static java.util.Objects.requireNonNull;

@ApiResource(name = "unwrapped", description = "An unwrapped object")
public record Unwrapped(@ApiDescription("count") int count, @ApiUnwrapped Name name)
{
    public Unwrapped
    {
        requireNonNull(name, "name is null");
    }
}
