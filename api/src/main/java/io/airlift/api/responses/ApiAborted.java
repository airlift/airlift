package io.airlift.api.responses;

import io.airlift.api.ApiResponse;

import java.util.Optional;

import static io.airlift.http.client.HttpStatus.CONFLICT;
import static java.util.Objects.requireNonNull;

@ApiResponse(name = "aborted", description = "Operation was aborted", status = CONFLICT)
public record ApiAborted(Optional<String> message)
{
    public ApiAborted
    {
        requireNonNull(message, "message is null");
    }
}
