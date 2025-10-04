package io.airlift.api.servertests.standard;

import io.airlift.api.ApiDescription;
import io.airlift.api.ApiResource;

import java.util.Optional;

@ApiResource(name = "thing", openApiAlternateName = "newThing", description = "things")
public record NewThing(
        @ApiDescription("the name") String name,
        @ApiDescription("the quantity") int qty,
        @ApiDescription("the optional code") Optional<String> code)
{
}
