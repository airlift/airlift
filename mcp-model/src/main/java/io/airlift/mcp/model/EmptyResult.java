package io.airlift.mcp.model;

import java.util.Map;
import java.util.Optional;

import static io.airlift.mcp.model.Meta.normalize;

public record EmptyResult(Optional<Map<String, Object>> meta)
        implements Meta<EmptyResult>
{
    public static final EmptyResult EMPTY_RESULT = new EmptyResult(Optional.empty());

    public EmptyResult
    {
        meta = normalize(meta);
    }

    @Override
    public EmptyResult withMeta(Map<String, Object> meta)
    {
        return new EmptyResult(Optional.of(meta));
    }
}
