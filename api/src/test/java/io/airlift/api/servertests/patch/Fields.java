package io.airlift.api.servertests.patch;

import io.airlift.api.ApiDescription;
import io.airlift.api.ApiReadOnly;
import io.airlift.api.ApiResource;

import java.util.Map;

@ApiReadOnly
@ApiResource(name = "package", openApiAlternateName = "packageFields", description = "dummy")
public record Fields(@ApiDescription("dummy") Map<String, String> fields)
{
}
