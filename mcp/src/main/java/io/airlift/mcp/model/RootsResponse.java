package io.airlift.mcp.model;

import com.google.common.collect.ImmutableList;

import java.util.List;

import static java.util.Objects.requireNonNull;

public record RootsResponse(List<Root> roots)
{
    public record Root(String uri, String name)
    {
        public Root
        {
            requireNonNull(uri, "uri is null");
            requireNonNull(name, "name is null");
        }
    }

    public RootsResponse
    {
        roots = ImmutableList.copyOf(roots);
    }
}
