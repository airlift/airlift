package io.airlift.mcp.model;

import java.util.Map;
import java.util.Optional;

import static io.airlift.mcp.model.Meta.normalize;
import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;

public record ReadResourceRequest(String uri, Optional<String> requestState, Optional<Map<String, Object>> inputResponses, Optional<Map<String, Object>> meta)
        implements Meta<ReadResourceRequest>, InputResponses<ReadResourceRequest>
{
    public ReadResourceRequest
    {
        requireNonNull(uri, "uri is null");
        requestState = requireNonNullElse(requestState, Optional.empty());
        inputResponses = normalize(inputResponses);
        meta = normalize(meta);
    }

    public ReadResourceRequest(String uri)
    {
        this(uri, Optional.empty());
    }

    public ReadResourceRequest(String uri, Optional<Map<String, Object>> meta)
    {
        this(uri, Optional.empty(), Optional.empty(), meta);
    }

    @Override
    public ReadResourceRequest withMeta(Map<String, Object> meta)
    {
        return new ReadResourceRequest(uri, requestState, inputResponses, Optional.of(meta));
    }

    @Override
    public ReadResourceRequest withInputResponses(Optional<String> requestState, Map<String, Object> inputResponses)
    {
        return new ReadResourceRequest(uri, requestState, Optional.of(inputResponses), meta);
    }
}
