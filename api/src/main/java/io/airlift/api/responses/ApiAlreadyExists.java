package io.airlift.api.responses;

import io.airlift.api.ApiResponse;

import java.util.Optional;

import static io.airlift.http.client.HttpStatus.CONFLICT;
import static java.util.Objects.requireNonNull;

@ApiResponse(name = "exists", description = "Already exists", status = CONFLICT)
public record ApiAlreadyExists(Optional<String> message)
{
    public ApiAlreadyExists
    {
        requireNonNull(message, "message is null");
    }
}
