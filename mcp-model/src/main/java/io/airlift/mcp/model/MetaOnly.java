package io.airlift.mcp.model;

import java.util.Map;
import java.util.Optional;

import static io.airlift.mcp.model.Meta.normalize;

public record MetaOnly(Optional<Map<String, Object>> meta)
        implements Meta<MetaOnly>
{
    public static final MetaOnly EMPTY_META = new MetaOnly(Optional.empty());

    public MetaOnly
    {
        meta = normalize(meta);
    }

    @Override
    public MetaOnly withMeta(Map<String, Object> meta)
    {
        return new MetaOnly(Optional.of(meta));
    }
}
