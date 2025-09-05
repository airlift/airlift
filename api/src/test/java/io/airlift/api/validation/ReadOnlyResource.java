package io.airlift.api.validation;

import io.airlift.api.ApiDescription;
import io.airlift.api.ApiReadOnly;
import io.airlift.api.ApiResource;

@ApiResource(name = "a", description = "b")
@ApiReadOnly
public record ReadOnlyResource(@ApiDescription("a") String hey)
{
}
