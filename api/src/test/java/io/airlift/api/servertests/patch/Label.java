package io.airlift.api.servertests.patch;

import io.airlift.api.ApiDescription;
import io.airlift.api.ApiResource;

@ApiResource(name = "label", description = "dummy")
public record Label(@ApiDescription("dummy") String name) {}
