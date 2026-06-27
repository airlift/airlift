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
        Implementation serverInfo,
        Optional<String> instructions,
        Optional<Map<String, Object>> meta)
        implements Meta
{
    public DiscoverResult
    {
        requireNonNull(resultType, "resultType is null");
        supportedVersions = ImmutableList.copyOf(supportedVersions);
        requireNonNull(capabilities, "capabilities is null");
        requireNonNull(serverInfo, "serverInfo is null");
        requireNonNull(instructions, "instructions is null");
        meta = requireNonNullElse(meta, Optional.empty());
    }

    @Override
    public Object withMeta(Map<String, Object> meta)
    {
        return new DiscoverResult(resultType, supportedVersions, capabilities, serverInfo, instructions, Optional.of(meta));
    }
}
