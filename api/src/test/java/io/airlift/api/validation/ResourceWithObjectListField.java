package io.airlift.api.validation;

import io.airlift.api.ApiDescription;
import io.airlift.api.ApiResource;

import java.util.List;

@ApiResource(name = "objectListField", description = "Resource with Object list field")
public record ResourceWithObjectListField(
        @ApiDescription("Free-form list") List<Object> payloads) {}
