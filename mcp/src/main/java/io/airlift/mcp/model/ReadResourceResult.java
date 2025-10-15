package io.airlift.mcp.model;

import com.google.common.collect.ImmutableList;
import java.util.List;

public record ReadResourceResult(List<ResourceContents> contents) {
    public ReadResourceResult {
        contents = ImmutableList.copyOf(contents);
    }
}
