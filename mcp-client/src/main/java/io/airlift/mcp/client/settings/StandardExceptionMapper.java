package io.airlift.mcp.client.settings;

import io.airlift.mcp.McpClientException;
import io.airlift.mcp.McpException;

import java.util.Optional;
import java.util.concurrent.TimeoutException;

import static com.google.common.base.Throwables.getRootCause;
import static io.airlift.mcp.McpException.exception;
import static io.airlift.mcp.model.JsonRpcErrorCode.INTERNAL_ERROR;
import static io.airlift.mcp.model.JsonRpcErrorCode.INVALID_REQUEST;
import static io.airlift.mcp.model.JsonRpcErrorCode.REQUEST_TIMEOUT;
import static java.util.Objects.requireNonNull;

public class StandardExceptionMapper
        implements ExceptionMapper
{
    @Override
    public RuntimeException mapException(Throwable exception)
    {
        requireNonNull(exception, "exception is null");

        Throwable throwable = mapException(exception, false);
        if (RuntimeException.class.isAssignableFrom(throwable.getClass())) {
            return (RuntimeException) throwable;
        }

        return exception(throwable);
    }

    private Throwable mapException(Throwable throwable, boolean isRootCause)
    {
        return switch (throwable) {
            case InterruptedException _ -> {
                Thread.currentThread().interrupt();
                yield throwable;
            }

            case TimeoutException _ -> exception(REQUEST_TIMEOUT, throwable, messageFromException(throwable));

            case IllegalStateException _, IllegalArgumentException _ -> exception(INVALID_REQUEST, throwable, messageFromException(throwable));

            case McpException mcpException -> mcpException;

            case McpClientException mcpClientException -> mcpClientException.unwrap();

            default -> {
                if (isRootCause) {
                    yield exception(INTERNAL_ERROR, throwable, messageFromException(throwable));
                }
                yield mapException(getRootCause(throwable), true);
            }
        };
    }

    private String messageFromException(Throwable throwable)
    {
        return Optional.ofNullable(throwable.getMessage())
                .orElse("Unknown error");
    }
}
