package io.airlift.api.servertests.standard;

import io.airlift.api.ApiDescription;
import io.airlift.api.ApiPolyResource;
import io.airlift.api.ApiResource;

import java.time.Instant;

@ApiPolyResource(key = "typeKey", name = "poly", description = "dummy")
public sealed interface PolyType
{
    @ApiResource(name = "typeOne", description = "dummy")
    record TypeOne(@ApiDescription("dummy") String name, @ApiDescription("dummy") String value)
            implements PolyType
    {
    }

    @ApiResource(name = "typeTwo", description = "dummy")
    record TypeTwo(@ApiDescription("dummy") Instant timestamp, @ApiDescription("dummy") int qty)
            implements PolyType
    {
    }
}
