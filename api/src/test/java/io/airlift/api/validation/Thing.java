package io.airlift.api.validation;

import io.airlift.api.ApiDescription;
import io.airlift.api.ApiResource;
import io.airlift.api.ApiResourceVersion;

@ApiResource(name = "thing", description = "thingy", quotas = "THING")
public record Thing(
        ApiResourceVersion syncToken,
        @ApiDescription("id") ThingId thingId,
        @ApiDescription("original name") String name,
        @ApiDescription("original description") String description)
{
}
