package io.airlift.mcp.model;

import com.fasterxml.jackson.annotation.JsonValue;
import com.google.common.collect.ImmutableList;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;

import static java.util.Locale.ROOT;
import static java.util.Objects.requireNonNull;

public record CreateMessageRequest(
        List<SamplingMessage> messages,
        Optional<ModelPreferences> modelPreferences,
        Optional<String> systemPrompt,
        Optional<ContextInclusionStrategy> includeContext,
        OptionalDouble temperature,
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

    public CreateMessageRequest(List<SamplingMessage> messages, int maxTokens)
    {
        this(messages, Optional.empty(), Optional.empty(), Optional.empty(), OptionalDouble.empty(), maxTokens, Optional.empty(), Optional.empty(), Optional.empty());
    }

    public CreateMessageRequest(SamplingMessage messages, int maxTokens)
    {
        this(ImmutableList.of(messages), Optional.empty(), Optional.empty(), Optional.empty(), OptionalDouble.empty(), maxTokens, Optional.empty(), Optional.empty(), Optional.empty());
    }

    public CreateMessageRequest(Role role, Content content, int maxTokens)
    {
        this(ImmutableList.of(new SamplingMessage(role, content)), Optional.empty(), Optional.empty(), Optional.empty(), OptionalDouble.empty(), maxTokens, Optional.empty(), Optional.empty(), Optional.empty());
    }

    public record SamplingMessage(Role role, Content content)
    {
        public SamplingMessage
        {
            requireNonNull(role, "role is null");
            requireNonNull(content, "content is null");
        }
    }

    public record ModelHint(String name)
    {
        public ModelHint
        {
            requireNonNull(name, "name is null");
        }
    }

    public record ModelPreferences(List<ModelHint> hints, OptionalDouble costPriority, OptionalDouble speedPriority, OptionalDouble intelligencePriority)
    {
        public ModelPreferences
        {
            hints = ImmutableList.copyOf(hints);
            requireNonNull(costPriority, "costPriority is null");
            requireNonNull(speedPriority, "speedPriority is null");
            requireNonNull(intelligencePriority, "intelligencePriority is null");
        }

        public ModelPreferences(List<ModelHint> hints)
        {
            this(hints, OptionalDouble.empty(), OptionalDouble.empty(), OptionalDouble.empty());
        }

        public ModelPreferences(ModelHint hint)
        {
            this(ImmutableList.of(hint), OptionalDouble.empty(), OptionalDouble.empty(), OptionalDouble.empty());
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
