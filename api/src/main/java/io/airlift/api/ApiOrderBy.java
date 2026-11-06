package io.airlift.api;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import io.airlift.api.responses.ApiException;

import java.util.List;
import java.util.Set;

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.joining;

public record ApiOrderBy<T>(List<Ordering<T>> orderings)
{
    public static final String ORDER_BY_PARAMETER_NAME = "orderBy";

    public ApiOrderBy
    {
        orderings = ImmutableList.copyOf(requireNonNull(orderings, "orderings is null"));
    }

    public ApiOrderBy(T field, ApiOrderByDirection direction)
    {
        this(ImmutableList.of(new Ordering<>(field, direction)));
    }

    @SafeVarargs
    public final void validate(T... allowedFields)
    {
        validate(ImmutableSet.copyOf(allowedFields));
    }

    public void validate(Set<T> allowedFields)
    {
        Set<T> invalidFields = orderings.stream()
                .map(Ordering::field)
                .filter(field -> !allowedFields.contains(field))
                .collect(toImmutableSet());

        if (!invalidFields.isEmpty()) {
            throw ApiException.badRequest("Invalid order by: " + invalidFields.stream().map(String::valueOf).collect(joining(", ")));
        }
    }

    public record Ordering<T>(T field, ApiOrderByDirection direction)
    {
        public Ordering
        {
            requireNonNull(field, "field is null");
            requireNonNull(direction, "type is null");
        }

        public String toQueryValue()
        {
            checkState(field instanceof String, "toQueryValue() requires a String field");
            return field + " " + direction.value();
        }

        public String toQueryValue(ApiEnumValueResolver enumValueResolver)
        {
            requireNonNull(enumValueResolver, "enumValueResolver is null");
            checkState(field instanceof Enum<?>, "toQueryValue(ApiEnumValueResolver) requires an enum field");
            return enumValueResolver.value((Enum<?>) field) + " " + direction.value();
        }
    }
}
