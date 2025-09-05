package io.airlift.api.servertests.noversions;

import io.airlift.api.ApiDescription;
import io.airlift.api.ApiResource;

@ApiResource(name = "resource", description = "Something")
public record ResourceWithoutVersion(@ApiDescription("id") ResourceId resourceId, @ApiDescription("name") String name)
{
}
