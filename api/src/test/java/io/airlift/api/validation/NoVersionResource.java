package io.airlift.api.validation;

import io.airlift.api.ApiDescription;
import io.airlift.api.ApiResource;

@ApiResource(name = "a", description = "b")
public record NoVersionResource(@ApiDescription("a") String hey)
{
}
