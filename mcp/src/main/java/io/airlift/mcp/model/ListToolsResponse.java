package io.airlift.mcp.model;

import com.google.common.collect.ImmutableList;

import java.util.List;

public record ListToolsResponse(List<Tool> tools)
{
    public ListToolsResponse
    {
        tools = ImmutableList.copyOf(tools);
    }
}
