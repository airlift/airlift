package io.airlift.mcp.model;

import com.google.common.collect.ImmutableList;

import java.util.List;
import java.util.Optional;

import static com.google.common.base.MoreObjects.firstNonNull;
import static java.util.Objects.requireNonNull;

public record Implementation(String name, String version, Optional<String> description, Optional<String> title, Optional<String> websiteUrl, Optional<List<Icon>> icons)
{
    public Implementation
    {
        requireNonNull(name, "name is null");
        requireNonNull(version, "version is null");
        description = firstNonNull(description, Optional.empty());
        title = firstNonNull(title, Optional.empty());
        websiteUrl = firstNonNull(websiteUrl, Optional.empty());
        icons = firstNonNull(icons, Optional.empty());
    }

    public Implementation(String name, String version)
    {
        this(name, version, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
    }

    public Implementation withAdditionalIcons(List<Icon> additionalIcons)
    {
        ImmutableList.Builder<Icon> builder = ImmutableList.builder();
        icons.ifPresent(builder::addAll);
        builder.addAll(additionalIcons);
        List<Icon> updatedIcons = builder.build();

        return new Implementation(name, version, description, title, websiteUrl, updatedIcons.isEmpty() ? Optional.empty() : Optional.of(updatedIcons));
    }

    public Implementation simpleForm()
    {
        return new Implementation(name, version, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
    }
}
