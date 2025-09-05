package io.airlift.api;

import io.airlift.api.ApiOrderBy.Ordering;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static io.airlift.api.responses.ApiException.badRequest;
import static java.util.Objects.requireNonNull;

public record ApiPagination(Optional<String> pageToken, int pageSize, Optional<Ordering> ordering)
{
    public static final String PAGE_TOKEN_QUERY_PARAMETER_NAME = "pageToken";
    public static final String PAGE_SIZE_QUERY_PARAMETER_NAME = "pageSize";

    public static final int DEFAULT_PAGE_SIZE = 100;

    public static final AtomicInteger MUTABLE_DEFAULT_PAGE_SIZE = new AtomicInteger(DEFAULT_PAGE_SIZE);

    public ApiPagination
    {
        requireNonNull(pageToken, "pageToken is null");
        requireNonNull(ordering, "ordering is null");

        pageSize = validatePageSize(pageSize);
    }

    public ApiPagination withOrdering(ApiOrderBy ordering)
    {
        return new ApiPagination(pageToken, pageSize, validateOrdering(ordering));
    }

    public ApiPagination next(ApiPaginatedResult<?> result)
    {
        String nextPageToken = result.nextPageToken();
        Optional<String> appliedNextPageToken = nextPageToken.equals(ApiPaginatedResult.EMPTY_PAGE_TOKEN) ? Optional.empty() : Optional.of(nextPageToken);
        return new ApiPagination(appliedNextPageToken, pageSize, ordering);
    }

    private Optional<Ordering> validateOrdering(ApiOrderBy ordering)
    {
        return switch (ordering.orderings().size()) {
            case 0 -> Optional.empty();
            case 1 -> Optional.of(ordering.orderings().getFirst());
            default -> throw badRequest("Too many order by clauses. Pagination allows only 1 order by clause.");
        };
    }

    private static int validatePageSize(int pageSize)
    {
        if (pageSize < 0) {
            throw badRequest("Invalid pageSize: " + pageSize);
        }

        return (pageSize == 0) ? MUTABLE_DEFAULT_PAGE_SIZE.get() : pageSize;
    }
}
