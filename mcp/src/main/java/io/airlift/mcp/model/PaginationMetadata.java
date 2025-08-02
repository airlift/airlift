package io.airlift.mcp.model;

import static com.google.common.base.Preconditions.checkArgument;

public record PaginationMetadata(int pageSize)
{
    public static final PaginationMetadata DEFAULT = new PaginationMetadata(100);

    public PaginationMetadata
    {
        checkArgument(pageSize > 0, "pageSize must be greater than 0");
    }

    public static PaginationMetadata noPagination()
    {
        return new PaginationMetadata(Integer.MAX_VALUE);
    }
}
