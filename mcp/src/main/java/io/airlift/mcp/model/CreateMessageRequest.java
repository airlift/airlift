package io.airlift.mcp.model;

import com.fasterxml.jackson.annotation.JsonValue;
import com.google.common.collect.ImmutableList;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;

import static java.util.Locale.ROOT;
import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;

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
        modelPreferences = requireNonNullElse(modelPreferences, Optional.empty());
        systemPrompt = requireNonNullElse(systemPrompt, Optional.empty());
        includeContext = requireNonNullElse(includeContext, Optional.empty());
        temperature = requireNonNullElse(temperature, OptionalDouble.empty());
        stopSequences = requireNonNullElse(stopSequences, Optional.empty());
        metadata = requireNonNullElse(metadata, Optional.empty());
        meta = requireNonNullElse(meta, Optional.empty());
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
            costPriority = requireNonNullElse(costPriority, OptionalDouble.empty());
            speedPriority = requireNonNullElse(speedPriority, OptionalDouble.empty());
            intelligencePriority = requireNonNullElse(intelligencePriority, OptionalDouble.empty());
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
