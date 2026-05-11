package io.airlift.api.validation;

import io.airlift.api.ApiDescription;
import io.airlift.api.ApiResource;
import io.airlift.api.ApiResourceVersion;

import java.util.List;
import java.util.Map;

@ApiResource(name = "resourceWithObject", description = "a resource with object container fields")
public record ResourceWithObject(
        ApiResourceVersion syncToken,
        @ApiDescription("id") ThingId thingId,
        @ApiDescription("free-form list") List<Object> dataList,
        @ApiDescription("free-form map") Map<String, Object> dataMap) {}
