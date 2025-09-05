package io.airlift.api.servertests.multipart;

import io.airlift.api.ApiDescription;
import io.airlift.api.ApiResource;

import static java.util.Objects.requireNonNull;

@ApiResource(name = "subpart", description = "yep")
public record SubPart(@ApiDescription("dummy") String a, @ApiDescription("dummy") int b)
{
    public SubPart
    {
        requireNonNull(a, "a is null");
    }
}
