package io.airlift.api.validation;

import io.airlift.api.ApiDescription;
import io.airlift.api.ApiResource;

@ApiResource(name = "food", description = "dummy", quotas = "FOOD")
public record FoodCreateRequest(@ApiDescription("name") String name)
{
}
