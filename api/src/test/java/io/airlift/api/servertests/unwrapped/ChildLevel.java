package io.airlift.api.servertests.unwrapped;

import io.airlift.api.ApiDescription;
import io.airlift.api.ApiResource;
import io.airlift.api.ApiUnwrapped;

import java.time.Instant;
import java.util.Optional;

@ApiResource(name = "child", description = "A child")
public record ChildLevel(
        @ApiDescription("unused") Instant timestamp,
        @ApiDescription("unused") double rate,
        @ApiDescription("Maybe a number") Optional<Integer> scale,
        @ApiUnwrapped ChildChildLevel childChild)
{
}
