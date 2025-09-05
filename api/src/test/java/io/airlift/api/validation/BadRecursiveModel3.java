package io.airlift.api.validation;

import io.airlift.api.ApiDescription;
import io.airlift.api.ApiResource;

import java.util.List;

@ApiResource(name = "recursiveModel", description = "A model that references itself")
public record BadRecursiveModel3(@ApiDescription("inner") List<Inner> child)
{
    @ApiResource(name = "inner", description = "A model that references itself")
    public record Inner(@ApiDescription("parent") BadRecursiveModel3 parent)
    {
    }
}
