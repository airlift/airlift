package io.airlift.api.validation;

import io.airlift.api.ApiDescription;
import io.airlift.api.ApiResource;
import io.airlift.api.ApiResourceVersion;

@ApiResource(name = "food", description = "dummy")
public record Food(ApiResourceVersion syncToken, @ApiDescription("id") FoodId foodId, @ApiDescription("name") String name)
{
}
