package io.airlift.api.servertests.multipart;

import com.google.common.collect.ImmutableMap;
import io.airlift.api.ApiDescription;
import io.airlift.api.ApiResource;
import io.airlift.api.ApiUnwrapped;

import java.time.Instant;
import java.util.Map;

import static java.util.Objects.requireNonNull;

@ApiResource(name = "part", description = "yep")
public record ThePart(
        @ApiDescription("dummy") String name,
        @ApiDescription("dummy") Instant dateTime,
        @ApiDescription("dummy") int qty,
        @ApiDescription("dummy") Map<String, String> attributes,
        @ApiUnwrapped SubPart subPart)
{
    public ThePart
    {
        requireNonNull(name, "name is null");
        requireNonNull(dateTime, "dateTime is null");
        requireNonNull(subPart, "subPart is null");
        attributes = ImmutableMap.copyOf(attributes);
    }
}
