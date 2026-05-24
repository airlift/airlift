package io.airlift.mcp.model;

import java.util.List;
import java.util.Optional;

import static java.util.Objects.requireNonNullElse;

public record SubscriptionFilter(Optional<Boolean> toolsListChanged, Optional<Boolean> promptsListChanged, Optional<Boolean> resourcesListChanged, Optional<List<String>> resourceSubscriptions)
{
    public SubscriptionFilter
    {
        toolsListChanged = requireNonNullElse(toolsListChanged, Optional.empty());
        promptsListChanged = requireNonNullElse(promptsListChanged, Optional.empty());
        resourcesListChanged = requireNonNullElse(resourcesListChanged, Optional.empty());
        resourceSubscriptions = requireNonNullElse(resourceSubscriptions, Optional.empty());
    }
}
