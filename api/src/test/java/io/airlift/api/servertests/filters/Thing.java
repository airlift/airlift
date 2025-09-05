package io.airlift.api.servertests.filters;

import io.airlift.api.ApiDescription;
import io.airlift.api.ApiResource;
import io.airlift.api.ApiResourceVersion;

@ApiResource(name = "thing", description = "things")
public record Thing(
        ApiResourceVersion syncToken,
        @ApiDescription("the id") ThingId thingId,
        @ApiDescription("the name") String name)
{
}
