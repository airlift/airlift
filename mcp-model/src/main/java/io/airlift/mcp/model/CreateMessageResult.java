package io.airlift.mcp.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Optional;

import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;

public record CreateMessageResult(Role role, Content content, String model, Optional<StopReason> stopReason)
{
    public CreateMessageResult
    {
        requireNonNull(role, "role is null");
        requireNonNull(content, "content is null");
        requireNonNull(model, "model is null");
        stopReason = requireNonNullElse(stopReason, Optional.empty());
    }

    public enum StopReason
    {
        END_TURN("endTurn"),
        STOP_SEQUENCE("stopSequence"),
        MAX_TOKENS("maxTokens"),
        UNKNOWN("unknown");

        private final String name;

        StopReason(String name)
        {
            this.name = requireNonNull(name, "name is null");
        }

        @JsonValue
        public String toJsonValue()
        {
            return this.name;
        }

        @JsonCreator
        public static StopReason fromJsonValue(String value)
        {
            return switch (value) {
                case "" -> null;
                case null -> null;
                default -> {
                    for (StopReason reason : StopReason.values()) {
                        if (reason.name.equals(value)) {
                            yield reason;
                        }
                    }
                    yield UNKNOWN;
                }
            };
        }
    }
}
