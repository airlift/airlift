package io.airlift.api.validation;

import io.airlift.api.ApiDescription;
import io.airlift.api.ApiResource;

import java.util.List;

@ApiResource(name = "objectValues", description = "Unwrapped object values")
public record ObjectValues(@ApiDescription("Free-form values") List<Object> values) {}
