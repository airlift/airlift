package io.airlift.api.validation;

import io.airlift.api.ApiDescription;
import io.airlift.api.ApiResource;
import io.airlift.api.ApiUnwrapped;

import java.util.List;
import java.util.Map;

@ApiResource(name = "objectField", description = "Resource with Object container fields")
public record ResourceWithObjectField(
        @ApiDescription("A name") String name,
        @ApiDescription("Free-form list") List<Object> payloads,
        @ApiDescription("Free-form map") Map<String, Object> attributes,
        @ApiUnwrapped List<ObjectValues> objectValues) {}
