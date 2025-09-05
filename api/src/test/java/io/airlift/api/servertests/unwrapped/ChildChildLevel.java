package io.airlift.api.servertests.unwrapped;

import io.airlift.api.ApiDescription;
import io.airlift.api.ApiResource;

@ApiResource(name = "childChild", description = "A child")
public record ChildChildLevel(@ApiDescription("unused") boolean flag)
{
}
