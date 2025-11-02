package io.airlift.api.responses;

import com.google.common.collect.ImmutableList;
import io.airlift.api.ApiDescription;
import io.airlift.api.ApiResponse;

import java.util.List;
import java.util.Optional;

import static io.airlift.http.client.HttpStatus.NOT_FOUND;
import static java.util.Objects.requireNonNull;

@ApiResponse(name = "notFound", description = "A resource or required component was not found", status = NOT_FOUND)
public record ApiNotFound(
        @ApiDescription("Name of the resource") String message,
        @ApiDescription("Violation description") Optional<String> description,
        @ApiDescription("Any fields/values that weren't found") Optional<List<String>> fields)
{
    public ApiNotFound
    {
        requireNonNull(message, "message is null");
        requireNonNull(description, "description is null");
        fields = fields.map(ImmutableList::copyOf);
    }
}
