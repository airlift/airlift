package io.airlift.mcp.model;

import com.google.common.collect.ImmutableMap;

import java.util.Map;
import java.util.Optional;

import static java.util.Objects.requireNonNullElse;

public record MetaOnly(Optional<Map<String, Object>> meta)
        implements Meta<MetaOnly>
{
    public static final MetaOnly EMPTY_META = new MetaOnly(Optional.empty());

    public MetaOnly
    {
        meta = requireNonNullElse(meta, Optional.<Map<String, Object>>empty()).map(ImmutableMap::copyOf);
    }

    @Override
    public MetaOnly withMeta(Map<String, Object> meta)
    {
        return new MetaOnly(Optional.of(meta));
    }
}
