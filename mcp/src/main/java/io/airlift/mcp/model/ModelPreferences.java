package io.airlift.mcp.model;

import com.google.common.collect.ImmutableList;

import java.util.List;
import java.util.Optional;

import static java.util.Objects.requireNonNull;

public record ModelPreferences(List<ModelHint> hints, Optional<Double> costPriority, Optional<Double> speedPriority, Optional<Double> intelligencePriority)
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
        this(hints, Optional.empty(), Optional.empty(), Optional.empty());
    }

    public ModelPreferences(ModelHint hint)
    {
        this(ImmutableList.of(hint), Optional.empty(), Optional.empty(), Optional.empty());
    }
}
