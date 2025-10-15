package io.airlift.mcp.model;

import com.google.common.collect.ImmutableList;
import java.util.List;

public record ListResourcesResult(List<Resource> resources) {
    public ListResourcesResult {
        resources = ImmutableList.copyOf(resources);
    }
}
