package io.airlift.mcp.client.internal;

import com.google.common.collect.ImmutableMap;
import io.airlift.mcp.model.Meta;

import java.util.Map;
import java.util.Optional;

// public for Jackson
public record MetaOnly(Optional<Map<String, Object>> meta)
        implements Meta<MetaOnly>
{
    @Override
    public MetaOnly withMeta(Map<String, Object> meta)
    {
        ImmutableMap.Builder<String, Object> builder = ImmutableMap.builder();
        this.meta.ifPresent(builder::putAll);
        builder.putAll(meta);
        return new MetaOnly(Optional.of(builder.buildKeepingLast()));
    }
}
