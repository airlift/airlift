package io.airlift.api.servertests.standard;

import io.airlift.api.ApiDescription;
import io.airlift.api.ApiResource;
import io.airlift.api.ApiResourceVersion;

import java.util.Optional;

@ApiResource(name = "thing", description = "things")
public record Thing(
        ApiResourceVersion syncToken,
        @ApiDescription("the id") ThingId thingId,
        @ApiDescription("the name") String name,
        @ApiDescription("the quantity") int qty,
        @ApiDescription("the optional code") Optional<String> code)
{
}
