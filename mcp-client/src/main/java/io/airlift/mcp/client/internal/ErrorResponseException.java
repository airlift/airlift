package io.airlift.mcp.client.internal;

import com.google.common.base.Throwables;
import io.airlift.http.client.UnexpectedResponseException;

import java.util.Optional;

/**
 * The cause of an {@link io.airlift.mcp.McpException} built from a JSON-RPC error the server sent. It records the
 * HTTP status the error came with, so that an error the server answered with can be told apart from a failure on
 * this side of the connection.
 */
public final class ErrorResponseException
        extends RuntimeException
{
    private final int statusCode;

    public ErrorResponseException(int statusCode)
    {
        super("Server responded with a JSON-RPC error, HTTP status " + statusCode);
        this.statusCode = statusCode;
    }

    public int statusCode()
    {
        return statusCode;
    }

    /**
     * The HTTP status of the server response a failure came from, or empty if it did not come from one.
     */
    public static Optional<Integer> responseStatusCode(Throwable failure)
    {
        return switch (Throwables.getRootCause(failure)) {
            case ErrorResponseException errorResponseException -> Optional.of(errorResponseException.statusCode());
            case UnexpectedResponseException unexpectedResponseException -> Optional.of(unexpectedResponseException.getStatusCode());
            default -> Optional.empty();
        };
    }
}
