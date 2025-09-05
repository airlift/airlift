package io.airlift.api.servertests.standard;

import io.airlift.api.ApiDescription;
import io.airlift.api.ApiResource;
import io.airlift.api.ApiResourceVersion;

import java.util.Optional;

@ApiResource(name = "thing", description = "things")
public record NewerThing(
        ApiResourceVersion version,
        @ApiDescription("the id") ThingId id,
        @ApiDescription("the name") String name,
        @ApiDescription("the quantity") int qty,
        @ApiDescription("the optional code") Optional<String> code,
        @ApiDescription("the optional setting") Optional<String> setting)
{
}
