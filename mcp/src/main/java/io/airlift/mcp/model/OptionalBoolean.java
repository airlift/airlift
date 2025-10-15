package io.airlift.mcp.model;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Optional;

public enum OptionalBoolean {
    UNDEFINED,
    TRUE,
    FALSE;

    @JsonValue
    public Optional<Boolean> toJsonValue() {
        return switch (this) {
            case UNDEFINED -> Optional.empty();
            case TRUE -> Optional.of(true);
            case FALSE -> Optional.of(false);
        };
    }

    public Boolean orElse(Boolean defaultValue) {
        return switch (this) {
            case UNDEFINED -> defaultValue;
            case TRUE -> true;
            case FALSE -> false;
        };
    }
}
