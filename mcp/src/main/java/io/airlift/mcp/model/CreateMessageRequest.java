package io.airlift.mcp.model;

import com.google.common.collect.ImmutableList;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static java.util.Objects.requireNonNull;

public record CreateMessageRequest(
        List<SamplingMessage> messages,
        ModelPreferences modelPreferences,
        String systemPrompt,
        Optional<ContextInclusionStrategy> includeContext,
        Optional<Double> temperature,
        int maxTokens,
        Optional<List<String>> stopSequences,
        Optional<Map<String, Object>> metadata,
        Optional<Map<String, Object>> meta)
        implements Meta
{
    public CreateMessageRequest
    {
        messages = ImmutableList.copyOf(messages);
        requireNonNull(modelPreferences, "modelPreferences is null");
        requireNonNull(systemPrompt, "systemPrompt is null");
        requireNonNull(includeContext, "includeContext is null");
        requireNonNull(temperature, "temperature is null");
        requireNonNull(stopSequences, "stopSequences is null");
        requireNonNull(metadata, "metadata is null");
        requireNonNull(meta, "meta is null");
    }

    public CreateMessageRequest(List<SamplingMessage> messages, ModelPreferences modelPreferences, String systemPrompt, int maxTokens)
    {
        this(messages, modelPreferences, systemPrompt, Optional.empty(), Optional.empty(), maxTokens, Optional.empty(), Optional.empty(), Optional.empty());
    }
}
