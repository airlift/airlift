package io.airlift.mcp.storage;

import static java.util.Objects.requireNonNull;

public record StorageGroupId(String group)
{
    public StorageGroupId
    {
        requireNonNull(group, "group is null");
    }
}
