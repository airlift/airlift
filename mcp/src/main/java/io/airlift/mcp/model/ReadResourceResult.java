package io.airlift.mcp.model;

import com.google.common.collect.ImmutableList;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static io.airlift.mcp.model.ResultType.COMPLETE;
import static java.util.Objects.requireNonNullElse;

public record ReadResourceResult(List<ResourceContents> contents, Optional<Map<String, Object>> meta)
        implements ReadResourceResponse
{
    public ReadResourceResult
    {
        contents = ImmutableList.copyOf(contents);
        meta = requireNonNullElse(meta, Optional.empty());
    }

    public ReadResourceResult(List<ResourceContents> contents)
    {
        this(contents, Optional.empty());
    }

    @Override
    public Optional<ResultType> resultType()
    {
        return Optional.of(COMPLETE);
    }

    @Override
    public ReadResourceResult withMeta(Map<String, Object> meta)
    {
        return new ReadResourceResult(contents, Optional.of(meta));
    }
}
