package io.airlift.mcp.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Optional;

import static com.google.common.base.MoreObjects.firstNonNull;
import static java.util.Objects.requireNonNull;

public record CreateMessageResult(Role role, Content content, String model, Optional<StopReason> stopReason)
{
    public CreateMessageResult
    {
        requireNonNull(role, "role is null");
        requireNonNull(content, "content is null");
        requireNonNull(model, "model is null");
        stopReason = firstNonNull(stopReason, Optional.empty());
    }

    public enum StopReason
    {
        END_TURN,
        STOP_SEQUENCE,
        MAX_TOKENS,
        UNKNOWN;

        @JsonValue
        public String toJsonValue()
        {
            return switch (this) {
                case END_TURN -> "endTurn";
                case STOP_SEQUENCE -> "stopSequence";
                case MAX_TOKENS -> "maxTokens";
                case UNKNOWN -> "unknown";
            };
        }

        @JsonCreator
        public static StopReason fromJsonValue(String value)
        {
            return switch (value) {
                case "endTurn" -> END_TURN;
                case "stopSequence" -> STOP_SEQUENCE;
                case "maxTokens" -> MAX_TOKENS;
                case "" -> null;
                case null -> null;
                default -> UNKNOWN;
            };
        }
    }
}
