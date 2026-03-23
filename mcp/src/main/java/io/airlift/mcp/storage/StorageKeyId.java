package io.airlift.mcp.storage;

import static java.util.Objects.requireNonNull;

public record StorageKeyId(String key)
{
    public StorageKeyId
    {
        requireNonNull(key, "key is null");
    }
}
