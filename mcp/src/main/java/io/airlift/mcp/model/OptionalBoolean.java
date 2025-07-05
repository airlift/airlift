package io.airlift.mcp.model;

import java.util.Optional;

public enum OptionalBoolean
{
    UNDEFINED,
    TRUE,
    FALSE;

    public Optional<Boolean> map()
    {
        return switch (this) {
            case UNDEFINED -> Optional.empty();
            case TRUE -> Optional.of(true);
            case FALSE -> Optional.of(false);
        };
    }
}
