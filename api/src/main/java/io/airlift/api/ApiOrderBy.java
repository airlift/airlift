package io.airlift.api;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import io.airlift.api.responses.ApiException;

import java.util.List;
import java.util.Set;

import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static java.util.Objects.requireNonNull;

public record ApiOrderBy(List<Ordering> orderings)
{
    public static final String ORDER_BY_PARAMETER_NAME = "orderBy";

    public ApiOrderBy
    {
        orderings = ImmutableList.copyOf(orderings);
    }

    public ApiOrderBy(String field, ApiOrderByDirection direction)
    {
        this(ImmutableList.of(new Ordering(field, direction)));
    }

    public void validate(String... allowedFields)
    {
        validate(ImmutableSet.copyOf(allowedFields));
    }

    public void validate(Set<String> allowedFields)
    {
        Set<String> invalidFields = orderings.stream()
                .map(Ordering::field)
                .filter(field -> !allowedFields.contains(field))
                .collect(toImmutableSet());

        if (!invalidFields.isEmpty()) {
            throw ApiException.badRequest("Invalid order by: " + String.join(", ", invalidFields));
        }
    }

    public record Ordering(String field, ApiOrderByDirection direction)
    {
        public Ordering
        {
            requireNonNull(field, "field is null");
            requireNonNull(direction, "type is null");
        }

        public String toQueryValue()
        {
            return field + " " + direction.value();
        }
    }
}
