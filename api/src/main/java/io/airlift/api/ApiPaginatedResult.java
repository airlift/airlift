package io.airlift.api;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.util.Objects.requireNonNull;

public record ApiPaginatedResult<T>(String nextPageToken, List<T> result, Optional<PaginationMetadata> paginationMetadata)
{
    public static final String EMPTY_PAGE_TOKEN = "";

    public record PaginationMetadata(int totalResults, boolean hasPrevious, boolean hasNext, Map<String, String> additionalProperties)
    {
        public PaginationMetadata
        {
            additionalProperties = ImmutableMap.copyOf(additionalProperties);
        }
    }

    public ApiPaginatedResult
    {
        requireNonNull(nextPageToken, "nextPageToken is null");
        result = ImmutableList.copyOf(result);
        requireNonNull(paginationMetadata, "paginationMetadata is null");
    }

    public ApiPaginatedResult(String nextPageToken, List<T> result)
    {
        this(nextPageToken, result, Optional.empty());
    }

    public ApiPaginatedResult<T> withPaginationMetadata(Optional<PaginationMetadata> paginationMetadata)
    {
        return new ApiPaginatedResult<>(nextPageToken, result, paginationMetadata);
    }

    public <U> ApiPaginatedResult<U> map(Function<T, U> mapper)
    {
        return new ApiPaginatedResult<>(nextPageToken, result.stream().map(mapper).collect(toImmutableList()), paginationMetadata);
    }

    public <U> ApiPaginatedResult<U> flatMap(Function<T, Stream<U>> flatMapper)
    {
        return new ApiPaginatedResult<>(nextPageToken, result.stream().flatMap(flatMapper).collect(toImmutableList()), paginationMetadata);
    }
}
