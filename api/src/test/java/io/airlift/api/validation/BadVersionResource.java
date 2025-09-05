package io.airlift.api.validation;

import io.airlift.api.ApiDescription;
import io.airlift.api.ApiResource;
import io.airlift.api.ApiResourceVersion;

@ApiResource(name = "bad", description = "also bad")
public record BadVersionResource(@ApiDescription("a") String hey, ApiResourceVersion notVersion)
{
}
