package io.airlift.mcp.model;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;

import static java.util.Objects.requireNonNull;

public record CreateMessageRequest(
        List<SamplingMessage> messages,
        Optional<ModelPreferences> modelPreferences,
        Optional<String> systemPrompt,
        ContextInclusionStrategy includeContext,
        OptionalDouble temperature,
        OptionalInt maxTokens,
        List<String> stopSequences,
        Map<String, Object> metadata)
{
    public CreateMessageRequest
    {
        requireNonNull(messages, "messages is null");
        requireNonNull(modelPreferences, "modelPreferences is null");
        requireNonNull(systemPrompt, "systemPrompt is null");
        requireNonNull(includeContext, "includeContext is null");
        requireNonNull(temperature, "temperature is null");
        requireNonNull(maxTokens, "maxTokens is null");
        stopSequences = ImmutableList.copyOf(stopSequences);
        metadata = ImmutableMap.copyOf(metadata);
    }

    public record SamplingMessage(Role role, Content content)
    {
        public SamplingMessage
        {
            requireNonNull(role, "role is null");
            requireNonNull(content, "content is null");
        }
    }

    public record ModelPreferences(List<ModelHint> hints, OptionalDouble costPriority, OptionalDouble speedPriority, OptionalDouble intelligencePriority)
    {
        public ModelPreferences
        {
            requireNonNull(hints, "hints is null");
            requireNonNull(costPriority, "costPriority is null");
            requireNonNull(speedPriority, "speedPriority is null");
            requireNonNull(intelligencePriority, "intelligencePriority is null");
        }
    }

    public record ModelHint(String name)
    {
        public ModelHint
        {
            requireNonNull(name, "name is null");
        }
    }

    public enum ContextInclusionStrategy
    {
        none,
        thisServer,
        allServers,
    }
}
