package io.airlift.api.validation;

import io.airlift.api.ApiDescription;
import io.airlift.api.ApiResource;

import java.util.Map;

@ApiResource(name = "recursiveModel", description = "A model that references itself")
public record BadRecursiveModel4(@ApiDescription("Children") Map<String, BadRecursiveModel4> children)
{
}
