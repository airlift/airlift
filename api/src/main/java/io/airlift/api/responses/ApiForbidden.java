package io.airlift.api.responses;

import io.airlift.api.ApiResponse;

import java.util.Optional;

import static io.airlift.http.client.HttpStatus.FORBIDDEN;
import static java.util.Objects.requireNonNull;

@ApiResponse(name = "forbidden", description = "Authorization failed", status = FORBIDDEN)
public record ApiForbidden(Optional<String> message)
{
    public ApiForbidden
    {
        requireNonNull(message, "message is null");
    }
}
