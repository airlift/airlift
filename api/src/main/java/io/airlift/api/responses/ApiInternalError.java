package io.airlift.api.responses;

import io.airlift.api.ApiDescription;
import io.airlift.api.ApiResponse;

import java.util.Optional;

import static io.airlift.http.client.HttpStatus.INTERNAL_SERVER_ERROR;
import static java.util.Objects.requireNonNull;

@ApiResponse(name = "internalError", description = "Unknown or internal error", status = INTERNAL_SERVER_ERROR)
public record ApiInternalError(@ApiDescription("Description of the error") Optional<String> details)
{
    public ApiInternalError
    {
        requireNonNull(details, "details is null");
    }
}
