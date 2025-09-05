package io.airlift.api.validation;

import io.airlift.api.ApiDescription;
import io.airlift.api.ApiResource;

import java.util.Optional;

@ApiResource(name = "recursiveModel", description = "A model that references itself")
public record BadRecursiveModel2(@ApiDescription("Child") Optional<BadRecursiveModel2> child)
{
}
