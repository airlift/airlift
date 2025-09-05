package io.airlift.api.responses;

import io.airlift.api.ApiDescription;
import io.airlift.api.ApiResponse;

import java.util.Optional;

import static io.airlift.http.client.HttpStatus.NOT_FOUND;
import static java.util.Objects.requireNonNull;

@ApiResponse(name = "notFound", description = "The specified resource was not found", status = NOT_FOUND)
public record ApiNotFound(
        @ApiDescription("Name of the resource") String resourceName,
        @ApiDescription("Violation description") Optional<String> description)
{
    public ApiNotFound
    {
        requireNonNull(resourceName, "resourceName is null");
        requireNonNull(description, "description is null");
    }
}
