package io.airlift.mcp.model;

import com.google.common.collect.ImmutableList;
import io.airlift.mcp.model.InitializeResult.ServerCapabilities;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;

public record DiscoverResult(
        ResultType resultType,
        List<String> supportedVersions,
        ServerCapabilities capabilities,
        Optional<String> instructions,
        Optional<Map<String, Object>> meta)
        implements Meta
{
    public DiscoverResult
    {
        requireNonNull(resultType, "resultType is null");
        supportedVersions = ImmutableList.copyOf(supportedVersions);
        requireNonNull(capabilities, "capabilities is null");
        instructions = requireNonNullElse(instructions, Optional.empty());
        meta = requireNonNullElse(meta, Optional.empty());
    }

    @Override
    public Object withMeta(Map<String, Object> meta)
    {
        return new DiscoverResult(resultType, supportedVersions, capabilities, instructions, Optional.of(meta));
    }
}
