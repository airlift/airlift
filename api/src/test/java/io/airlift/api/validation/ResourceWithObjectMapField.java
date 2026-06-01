package io.airlift.api.validation;

import io.airlift.api.ApiDescription;
import io.airlift.api.ApiResource;

import java.util.Map;

@ApiResource(name = "objectMapField", description = "Resource with Object map field")
public record ResourceWithObjectMapField(
        @ApiDescription("Free-form map") Map<String, Object> attributes) {}
