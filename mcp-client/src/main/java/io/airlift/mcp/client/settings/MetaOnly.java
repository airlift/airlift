package io.airlift.mcp.client.settings;

import com.google.common.collect.ImmutableMap;
import io.airlift.mcp.model.Meta;

import java.util.Map;
import java.util.Optional;

import static io.airlift.mcp.model.Meta.normalize;

// public for Jackson
public record MetaOnly(Optional<Map<String, Object>> meta)
        implements Meta<MetaOnly>
{
    public MetaOnly
    {
        meta = normalize(meta);
    }

    public MetaOnly()
    {
        this(Optional.empty());
    }

    public MetaOnly(Map<String, Object> meta)
    {
        this(Optional.of((meta)));
    }

    @Override
    public MetaOnly withMeta(Map<String, Object> meta)
    {
        ImmutableMap.Builder<String, Object> builder = ImmutableMap.builder();
        this.meta.ifPresent(builder::putAll);
        builder.putAll(meta);
        return new MetaOnly(Optional.of(builder.buildKeepingLast()));
    }
}
