package io.airlift.api.validation;

import io.airlift.api.ApiDescription;
import io.airlift.api.ApiPolyResource;
import io.airlift.api.ApiReadOnly;
import io.airlift.api.ApiResource;

@ApiPolyResource(key = "nested", name = "wayneRooney", description = "Glory glory")
@ApiReadOnly
public sealed interface NestedPoly
{
    @ApiResource(name = "notOk", description = "ok")
    @ApiReadOnly
    record Hi(@ApiDescription("hi") String hi)
            implements NestedPoly
    {
    }
}
