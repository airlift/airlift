package io.airlift.api.servertests.openapi;

import io.airlift.api.ApiResource;

@ApiResource(name = "dummy", description = "A dummy resource for testing")
public record DummyResource()
{
}
