package io.airlift.api.validation;

import io.airlift.api.ApiDescription;
import io.airlift.api.ApiResource;

@ApiResource(name = "recursiveModel", description = "A model that references itself")
public record BadRecursiveModel1(@ApiDescription("Child") BadRecursiveModel1 child)
{
}
