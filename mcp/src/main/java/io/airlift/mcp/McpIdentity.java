package io.airlift.mcp;

import java.util.List;

import static java.util.Objects.requireNonNull;

public sealed interface McpIdentity
{
    record Authenticated<T>(T identity)
            implements McpIdentity
    {
        public Authenticated
        {
            requireNonNull(identity, "identity is null");
        }

        public static <T> Authenticated<T> authenticated(T identity)
        {
            return new Authenticated<>(identity);
        }
    }

    record Unauthenticated(String message, List<String> authenticateHeaders)
            implements McpIdentity
    {
        public Unauthenticated
        {
            requireNonNull(message, "message is null");
            requireNonNull(authenticateHeaders, "authenticateHeaders is null");
        }

        public static Unauthenticated unauthenticated(String message, List<String> authenticateHeaders)
        {
            return new Unauthenticated(message, List.copyOf(authenticateHeaders));
        }
    }

    record Unauthorized(String message)
            implements McpIdentity
    {
        public Unauthorized
        {
            requireNonNull(message, "message is null");
        }

        public static Unauthorized unauthorized(String message)
        {
            return new Unauthorized(message);
        }
    }

    record Error(McpException cause)
            implements McpIdentity
    {
        public Error
        {
            requireNonNull(cause, "cause is null");
        }

        public static Error error(McpException cause)
        {
            return new Error(cause);
        }
    }
}
