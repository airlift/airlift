package io.airlift.api.compatibility.unwrapped;

import io.airlift.api.ApiDescription;
import io.airlift.api.ApiResource;

import static java.util.Objects.requireNonNull;

@ApiResource(name = "name", description = "A name")
public record Name(@ApiDescription("first name") String first, @ApiDescription("last name") String last)
{
    public Name
    {
        requireNonNull(first, "first is null");
        requireNonNull(last, "last is null");
    }
}
