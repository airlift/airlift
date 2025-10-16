package io.airlift.mcp.model;

import static java.util.Objects.requireNonNull;

import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.OptionalDouble;

public record Annotations(List<Role> audience, OptionalDouble priority) {
    public static final Annotations EMPTY = new Annotations(ImmutableList.of(), OptionalDouble.empty());

    public Annotations {
        audience = ImmutableList.copyOf(audience);
        requireNonNull(priority, "priority is null");
    }
}
