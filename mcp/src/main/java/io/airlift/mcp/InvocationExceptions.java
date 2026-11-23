package io.airlift.mcp;

import io.airlift.mcp.model.JsonRpcErrorDetail;
import tools.jackson.core.JacksonException;

import java.lang.reflect.InvocationTargetException;
import java.util.Set;

import static com.google.common.collect.Sets.newIdentityHashSet;
import static io.airlift.mcp.McpException.exception;
import static io.airlift.mcp.model.JsonRpcErrorCode.INTERNAL_ERROR;

/**
 * How a failure of a tool, prompt, resource or completion handler is reported, so that a synchronous call
 * and a task report the same failure the same way.
 */
public final class InvocationExceptions
{
    private InvocationExceptions() {}

    /**
     * Maps what a handler threw to what is reported for it, from the root of its cause chain:
     * <ul>
     *     <li>a self-contained {@link McpException} is kept, as a protocol error</li>
     *     <li>any other {@link McpException}, or a {@link RuntimeException}, becomes an {@link McpClientException},
     *     reported in-band with its own message</li>
     *     <li>a Jackson exception, a checked exception or an {@link Error} becomes an {@link McpException} - a
     *     protocol error with {@code internalErrorMessage}, as its own message may say more than a client should see</li>
     * </ul>
     *
     * @return an {@link McpClientException} or an {@link McpException}
     */
    public static RuntimeException mapInvocationException(Throwable throwable, String internalErrorMessage)
    {
        Throwable rootCause = rootCause(throwable);
        return switch (rootCause) {
            case McpException mcpException -> mcpException.isSelfContained() ? mcpException : new McpClientException(mcpException);
            // Jackson 3 exceptions are unchecked; keep reporting them as protocol errors as with Jackson 2's checked exceptions
            case JacksonException jacksonException -> new McpException(jacksonException, new JsonRpcErrorDetail(INTERNAL_ERROR, internalErrorMessage));
            case RuntimeException runtimeException -> new McpClientException(exception(runtimeException));
            case InvocationTargetException invocationTargetException -> {
                Throwable targetException = invocationTargetException.getTargetException();
                if (targetException instanceof McpException mcpException) {
                    yield mcpException;
                }
                yield new McpClientException(exception(targetException));
            }
            default -> new McpException(rootCause, new JsonRpcErrorDetail(INTERNAL_ERROR, internalErrorMessage));
        };
    }

    // as Guava's getRootCause, except that a chain that loops back on itself has no root - this maps the
    // throwable itself then, where getRootCause would throw and leave the failure unreported
    private static Throwable rootCause(Throwable throwable)
    {
        Set<Throwable> seen = newIdentityHashSet();
        Throwable rootCause = throwable;
        while (rootCause.getCause() != null) {
            if (!seen.add(rootCause)) {
                return throwable;
            }
            rootCause = rootCause.getCause();
        }
        return rootCause;
    }
}
