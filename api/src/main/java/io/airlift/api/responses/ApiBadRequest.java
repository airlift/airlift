package io.airlift.api.responses;

import io.airlift.api.ApiDescription;
import io.airlift.api.ApiResponse;

import java.util.Optional;

import static io.airlift.http.client.HttpStatus.BAD_REQUEST;
import static java.util.Objects.requireNonNull;

@ApiResponse(name = "badRequest", description = "Invalid request", status = BAD_REQUEST)
public record ApiBadRequest(@ApiDescription("Description of the error") Optional<String> details)
{
    public ApiBadRequest
    {
        requireNonNull(details, "details is null");
    }
}
