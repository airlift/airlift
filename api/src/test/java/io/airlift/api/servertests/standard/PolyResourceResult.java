package io.airlift.api.servertests.standard;

import io.airlift.api.ApiDescription;
import io.airlift.api.ApiResource;
import io.airlift.api.ApiResourceVersion;

@ApiResource(name = "poly", openApiAlternateName = "polyResultWithId", description = "dummy")
public record PolyResourceResult(ApiResourceVersion syncToken, @ApiDescription("dummy") PolyResourceId polyId)
{
}
