package io.airlift.mcp.model;

import com.google.common.collect.ImmutableList;
import io.airlift.mcp.model.ListRootsResult.Root;

import java.util.List;

public record RootsList(List<Root> roots)
{
    public RootsList
    {
        roots = ImmutableList.copyOf(roots);
    }
}
