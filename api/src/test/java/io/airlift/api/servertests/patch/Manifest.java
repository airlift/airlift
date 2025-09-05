package io.airlift.api.servertests.patch;

import io.airlift.api.ApiDescription;
import io.airlift.api.ApiReadOnly;
import io.airlift.api.ApiResource;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@ApiReadOnly
@ApiResource(name = "manifest", description = "dummy")
public record Manifest(
        @ApiDescription("name") String name,
        @ApiReadOnly @ApiDescription("dummy") Optional<Instant> creation,
        @ApiDescription("dummy") List<String> codes)
{
}
