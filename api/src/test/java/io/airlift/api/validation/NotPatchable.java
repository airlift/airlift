package io.airlift.api.validation;

import io.airlift.api.ApiDescription;
import io.airlift.api.ApiResource;
import io.airlift.api.ApiResourceVersion;

import java.util.List;

@ApiResource(name = "thing", description = "dummy")
public record NotPatchable(ApiResourceVersion syncToken, @ApiDescription("dummy") ThingId thingId, @ApiDescription("dummy") List<Internal> internals)
{
    @ApiResource(name = "internal", description = "dummy")
    public record Internal(@ApiDescription("dummy") String name)
    {
    }
}
