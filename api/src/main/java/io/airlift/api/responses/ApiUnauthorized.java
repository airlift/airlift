package io.airlift.api.responses;

import io.airlift.api.ApiResponse;

import java.util.Optional;

import static io.airlift.http.client.HttpStatus.UNAUTHORIZED;
import static java.util.Objects.requireNonNull;

@ApiResponse(name = "unauthorized", description = "Authorization failed", status = UNAUTHORIZED)
public record ApiUnauthorized(Optional<String> message)
{
    public ApiUnauthorized
    {
        requireNonNull(message, "message is null");
    }
}
