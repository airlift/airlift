package io.airlift.api.openapi;

import io.airlift.api.model.ModelMethod;
import io.airlift.api.model.ModelService;
import io.airlift.api.openapi.models.Operation;

public interface OpenApiExtensionFilter
{
    Operation apply(ModelService modelService, ModelMethod modelMethod, Operation operation);
}
