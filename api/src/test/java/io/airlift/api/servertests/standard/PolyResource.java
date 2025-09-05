package io.airlift.api.servertests.standard;

import io.airlift.api.ApiDescription;
import io.airlift.api.ApiResource;

@ApiResource(name = "poly", openApiAlternateName = "polyResult", description = "dummy", quotas = "POLY")
public record PolyResource(@ApiDescription("dummy") PolyType poly)
{
}
