package io.airlift.mcp.model;

import com.google.common.collect.ImmutableList;
import java.util.List;

public record ListPromptsResult(List<Prompt> prompts) {
    public ListPromptsResult {
        prompts = ImmutableList.copyOf(prompts);
    }
}
