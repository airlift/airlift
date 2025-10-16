package io.airlift.mcp.model;

import com.google.common.collect.ImmutableList;
import java.util.List;

public record ListToolsResult(List<Tool> tools) {
    public ListToolsResult {
        tools = ImmutableList.copyOf(tools);
    }
}
