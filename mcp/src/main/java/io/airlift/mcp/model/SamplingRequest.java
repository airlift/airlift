package io.airlift.mcp.model;

import com.fasterxml.jackson.annotation.JsonValue;
import com.google.common.collect.ImmutableList;
import io.airlift.mcp.model.Content.TextContent;

import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

import static java.util.Locale.ROOT;
import static java.util.Objects.requireNonNull;

public record SamplingRequest(
        List<SamplingMessage> messages,
        Optional<ModelPreferences> modelPreferences,
        Optional<ContextInclusionStrategy> includeContext,
        int maxTokens,
        Optional<List<String>> stopSequences,
        Optional<String> systemPrompt,
        OptionalDouble temperature)
{
    public SamplingRequest
    {
        messages = ImmutableList.copyOf(messages);
        requireNonNull(modelPreferences, "modelPreferences is null");
        requireNonNull(includeContext, "includeContext is null");
        requireNonNull(stopSequences, "stopSequences is null");
        requireNonNull(systemPrompt, "systemPrompt is null");
        requireNonNull(temperature, "temperature is null");
    }

    public SamplingRequest(List<SamplingMessage> messages, int maxTokens)
    {
        this(messages, Optional.empty(), Optional.empty(), maxTokens, Optional.empty(), Optional.empty(), OptionalDouble.empty());
    }

    public SamplingRequest(SamplingMessage message, int maxTokens)
    {
        this(ImmutableList.of(message), Optional.empty(), Optional.empty(), maxTokens, Optional.empty(), Optional.empty(), OptionalDouble.empty());
    }

    public SamplingRequest(String message, int maxTokens)
    {
        this(ImmutableList.of(new SamplingMessage(message)), Optional.empty(), Optional.empty(), maxTokens, Optional.empty(), Optional.empty(), OptionalDouble.empty());
    }

    public record SamplingMessage(Content content, Role role)
    {
        public SamplingMessage
        {
            requireNonNull(role, "role is null");
            requireNonNull(content, "content is null");
        }

        public SamplingMessage(Content content)
        {
            this(content, Role.USER);
        }

        public SamplingMessage(String content)
        {
            this(new TextContent(content), Role.USER);
        }
    }

    public record ModelHint(String name)
    {
        public ModelHint
        {
            requireNonNull(name, "name is null");
        }
    }

    public record ModelPreferences(Optional<List<ModelHint>> hints, OptionalDouble costPriority, OptionalDouble speedPriority, OptionalDouble intelligencePriority)
    {
        public ModelPreferences
        {
            requireNonNull(hints, "hints is null");
            requireNonNull(costPriority, "costPriority is null");
            requireNonNull(speedPriority, "speedPriority is null");
            requireNonNull(intelligencePriority, "intelligencePriority is null");
        }
    }

    public enum ContextInclusionStrategy
    {
        NONE,
        THIS_SERVER,
        ALL_SERVERS;

        @JsonValue
        public String toJsonValue()
        {
            return name().toLowerCase(ROOT);
        }
    }
}
