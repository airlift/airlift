package io.airlift.mcp.client.internal.legacy;

import java.util.Optional;

import static java.util.Objects.requireNonNull;

record LegacySessionId(Optional<String> sessionId)
{
    LegacySessionId
    {
        requireNonNull(sessionId, "sessionId is null");
    }
}
