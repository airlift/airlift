package io.airlift.api.model;

import io.airlift.http.client.HttpStatus;

import static java.util.Objects.requireNonNull;

public record ModelResponse(HttpStatus status, Class<?> responseClass, ModelResource resource)
{
    public ModelResponse
    {
        requireNonNull(status, "status is null");
        requireNonNull(responseClass, "responseClass is null");
        requireNonNull(resource, "resource is null");
    }
}
