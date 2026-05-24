package io.airlift.mcp.operations.legacy;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import io.airlift.mcp.McpConfig;
import io.airlift.mcp.model.InputRequest;
import io.airlift.mcp.model.InputRequests;
import io.airlift.mcp.model.JsonRpcErrorDetail;
import io.airlift.mcp.model.JsonRpcResponse;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import java.util.function.BiFunction;

import static com.google.common.base.Preconditions.checkArgument;
import static io.airlift.mcp.McpException.exception;
import static io.airlift.mcp.McpException.exceptionWithData;
import static io.airlift.mcp.model.JsonRpcErrorCode.INTERNAL_ERROR;
import static io.airlift.mcp.model.JsonRpcErrorCode.REQUEST_TIMEOUT;
import static io.airlift.mcp.operations.OperationsImpl.isInputRequired;

public class MrtrEmulator
{
    private static final Duration POLL_INTERVAL = Duration.ofSeconds(10);

    private final Duration timeout;

    @Inject
    MrtrEmulator(McpConfig config)
    {
        timeout = config.getEventStreamingTimeout().toJavaTime();
    }

    <T extends InputRequests> T emulate(LegacyRequestContextImpl requestContext, T result, BiFunction<Optional<String>, Map<String, Object>, T> handler)
    {
        while (isInputRequired(result)) {
            Map<String, InputRequest> inputRequests = result.inputRequests().orElseGet(ImmutableMap::of);
            checkArgument(!inputRequests.isEmpty(), "Input requests may not be empty for MRTR emulation");

            Map<String, Object> inputResponses = new HashMap<>();
            inputRequests.forEach((key, inputRequest) -> {
                try {
                    JsonRpcResponse<Object> response = requestContext.serverToClientRequest(inputRequest.method(), inputRequest.params(), Object.class, timeout, POLL_INTERVAL);
                    if (response.error().isPresent()) {
                        JsonRpcErrorDetail errorDetail = response.error().orElseThrow();
                        throw exceptionWithData(errorDetail.code(), errorDetail.message(), errorDetail.data());
                    }
                    if (response.result().isEmpty()) {
                        throw exception(INTERNAL_ERROR, "Server-to-client request '%s' returned neither a result nor an error".formatted(inputRequest.method()));
                    }
                    inputResponses.put(key, response.result().orElseThrow());
                }
                catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw exception(INTERNAL_ERROR, e, "Interrupted while waiting for server-to-client request");
                }
                catch (TimeoutException e) {
                    throw exception(REQUEST_TIMEOUT, e);
                }
            });

            result = handler.apply(result.requestState(), ImmutableMap.copyOf(inputResponses));
        }
        return result;
    }
}
