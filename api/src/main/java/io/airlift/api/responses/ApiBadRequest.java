package io.airlift.api.responses;

import com.google.common.collect.ImmutableList;
import io.airlift.api.ApiDescription;
import io.airlift.api.ApiResponse;

import java.util.List;
import java.util.Optional;

import static io.airlift.http.client.HttpStatus.BAD_REQUEST;
import static java.util.Objects.requireNonNull;

@ApiResponse(name = "badRequest", description = "Invalid request", status = BAD_REQUEST)
public record ApiBadRequest(
        @ApiDescription("Description of the error") Optional<String> message,
        @ApiDescription("Any fields/values that are invalid or incorrect") Optional<List<String>> fields)
{
    public ApiBadRequest
    {
        requireNonNull(message, "message is null");
        fields = fields.map(ImmutableList::copyOf);
    }
}
