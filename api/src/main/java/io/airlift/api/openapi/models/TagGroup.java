package io.airlift.api.openapi.models;

import java.util.List;

import static java.util.Objects.requireNonNull;

public record TagGroup(String name, List<String> tags)
{
    public TagGroup
    {
        requireNonNull(name, "name is null");
        tags = List.copyOf(tags);
    }
}
