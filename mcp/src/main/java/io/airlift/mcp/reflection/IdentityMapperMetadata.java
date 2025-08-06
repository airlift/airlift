package io.airlift.mcp.reflection;

import static java.util.Objects.requireNonNull;

public record IdentityMapperMetadata(Class<?> identityType)
{
    public IdentityMapperMetadata
    {
        requireNonNull(identityType, "identityType is null");
    }
}
