package io.airlift.api.validation;

import io.airlift.api.ApiDescription;
import io.airlift.api.ApiPolyResource;
import io.airlift.api.ApiReadOnly;
import io.airlift.api.ApiResource;

import java.util.List;

@ApiPolyResource(key = "checkit", name = "ryanGiggs", description = "Glory glory")
public sealed interface PolyInPoly
{
    @ApiResource(name = "ok", description = "ok")
    @ApiReadOnly
    record HasNested(@ApiReadOnly @ApiDescription("hi") List<NestedPoly> nestedPolys)
            implements PolyInPoly
    {
    }
}
