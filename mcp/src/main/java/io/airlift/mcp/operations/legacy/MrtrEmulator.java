package io.airlift.mcp.operations.legacy;

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

import static io.airlift.mcp.McpException.exception;
import static io.airlift.mcp.model.JsonRpcErrorCode.INTERNAL_ERROR;
import static io.airlift.mcp.model.JsonRpcErrorCode.REQUEST_TIMEOUT;
import static io.airlift.mcp.model.ResultType.COMPLETE;
import static io.airlift.mcp.model.ResultType.INPUT_REQUIRED;

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
        while (result.resultType().orElse(COMPLETE) == INPUT_REQUIRED) {
            Map<String, InputRequest> inputRequests = result.inputRequests().orElseThrow(() -> new IllegalArgumentException("Input requests may not be empty"));
            Map<String, Object> inputResponses = new HashMap<>();
            inputRequests.forEach((key, inputRequest) -> {
                try {
                    JsonRpcResponse<Object> response = requestContext.serverToClientRequest(inputRequest.method(), inputRequest.params(), Object.class, timeout, POLL_INTERVAL);
                    if (response.error().isPresent()) {
                        JsonRpcErrorDetail errorDetail = response.error().get();
                        throw exception(errorDetail.code(), errorDetail.message(), errorDetail.data());
                    }
                    inputResponses.put(key, response.result().orElse(null));
                }
                catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw exception(INTERNAL_ERROR, "Interrupted while waiting for server-to-client request");
                }
                catch (TimeoutException e) {
                    throw exception(REQUEST_TIMEOUT, e);
                }
            });

            result = handler.apply(result.requestState(), inputResponses);
        }
        return result;
    }
}
