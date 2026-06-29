package io.airlift.api;

import com.google.common.collect.ImmutableList;

import java.util.List;

import static java.util.Objects.requireNonNull;

public record TypedApiOrderBy<T>(List<Ordering<T>> orderings)
{
    public TypedApiOrderBy
    {
        orderings = ImmutableList.copyOf(orderings);
    }

    public TypedApiOrderBy(T field, ApiOrderByDirection direction)
    {
        this(ImmutableList.of(new Ordering<>(field, direction)));
    }

    public record Ordering<T>(T field, ApiOrderByDirection direction)
    {
        public Ordering
        {
            requireNonNull(field, "field is null");
            requireNonNull(direction, "direction is null");
        }
    }
}
