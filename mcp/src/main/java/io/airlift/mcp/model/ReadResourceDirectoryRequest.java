package io.airlift.mcp.model;

import java.util.Map;
import java.util.Optional;

import static io.airlift.mcp.model.Meta.normalize;
import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;

public record ReadResourceDirectoryRequest(String uri, Optional<String> cursor, Optional<Map<String, Object>> meta)
        implements PaginatedRequest, Meta<ReadResourceDirectoryRequest>
{
    public ReadResourceDirectoryRequest
    {
        requireNonNull(uri, "uri is null");
        cursor = requireNonNullElse(cursor, Optional.empty());
        meta = normalize(meta);
    }

    public ReadResourceDirectoryRequest(String uri)
    {
        this(uri, Optional.empty(), Optional.empty());
    }

    @Override
    public ReadResourceDirectoryRequest withMeta(Map<String, Object> meta)
    {
        return new ReadResourceDirectoryRequest(uri, cursor, Optional.of(meta));
    }
}
