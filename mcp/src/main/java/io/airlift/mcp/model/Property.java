package io.airlift.mcp.model;

import java.util.Optional;

public record Property()
{
    public static final Optional<Property> INSTANCE = Optional.of(new Property());
}
