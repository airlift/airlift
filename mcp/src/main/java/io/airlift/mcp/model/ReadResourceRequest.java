package io.airlift.mcp.model;

import java.util.Map;
import java.util.Optional;

import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;

public record ReadResourceRequest(String uri, Optional<Map<String, InputResponse>> inputResponses, Optional<String> requestState, Optional<Map<String, Object>> meta)
        implements Meta, InputResponsable<ReadResourceRequest>
{
    public ReadResourceRequest
    {
        requireNonNull(uri, "uri is null");
        inputResponses = requireNonNullElse(inputResponses, Optional.empty());
        requestState = requireNonNullElse(requestState, Optional.empty());
        meta = requireNonNullElse(meta, Optional.empty());
    }

    public ReadResourceRequest(String uri)
    {
        this(uri, Optional.empty(), Optional.empty(), Optional.empty());
    }

    @Override
    public ReadResourceRequest withInputResponses(InputResponses inputResponses)
    {
        return new ReadResourceRequest(uri, Optional.of(inputResponses.inputResponses()), inputResponses.requestState(), meta);
    }

    @Override
    public ReadResourceRequest withMeta(Map<String, Object> meta)
    {
        return new ReadResourceRequest(uri, inputResponses, requestState, Optional.of(meta));
    }
}
