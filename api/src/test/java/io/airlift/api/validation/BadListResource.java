package io.airlift.api.validation;

import io.airlift.api.ApiDescription;
import io.airlift.api.ApiResource;
import io.airlift.api.ApiResourceVersion;

import java.util.List;

@ApiResource(name = "badlists", description = "resource containing bad list")
public record BadListResource(ApiResourceVersion syncToken, @ApiDescription("id") BadListResourceId badlistsId, @ApiDescription("food list") List<Food> foodList)
{
}
