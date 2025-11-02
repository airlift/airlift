package io.airlift.api.responses;

import io.airlift.api.ApiResponse;

import java.util.Optional;

import static io.airlift.http.client.HttpStatus.TOO_MANY_REQUESTS;
import static java.util.Objects.requireNonNull;

@ApiResponse(name = "busy", description = "Too many requests or server is busy. Try again later.", status = TOO_MANY_REQUESTS)
public record ApiTooManyRequests(Optional<String> message)
{
    public ApiTooManyRequests
    {
        requireNonNull(message, "message is null");
    }
}
