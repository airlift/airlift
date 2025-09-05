package io.airlift.api.servertests.unwrapped;

import io.airlift.api.ApiDescription;
import io.airlift.api.ApiResource;
import io.airlift.api.ApiUnwrapped;

@ApiResource(name = "top", description = "unused")
public record TopLevel(
        @ApiDescription("unused") String name,
        @ApiDescription("unused") int age,
        @ApiUnwrapped ChildLevel childLevel)
{
}
