package io.airlift.api.servertests.standard;

import io.airlift.api.ApiDescription;
import io.airlift.api.ApiResource;
import io.airlift.api.ApiResourceVersion;

@ApiResource(name = "name", description = "foo")
public record Name(ApiResourceVersion syncToken, @ApiDescription("dummy") NameId nameId)
{
}
