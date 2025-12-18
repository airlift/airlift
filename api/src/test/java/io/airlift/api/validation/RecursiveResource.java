package io.airlift.api.validation;

import io.airlift.api.ApiDescription;
import io.airlift.api.ApiResource;

@ApiResource(name = "test", description = "foo")
public record RecursiveResource(@ApiDescription("foo") RecursiveResourceBase base)
{
}
