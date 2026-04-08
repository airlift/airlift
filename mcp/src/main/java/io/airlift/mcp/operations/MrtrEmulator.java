package io.airlift.mcp.operations;

import com.google.common.base.Stopwatch;
import com.google.inject.Inject;
import io.airlift.log.Logger;
import io.airlift.mcp.McpConfig;
import io.airlift.mcp.model.InputRequest;
import io.airlift.mcp.model.InputRequests;
import io.airlift.mcp.model.InputResponsable;
import io.airlift.mcp.model.InputResponse;
import io.airlift.mcp.model.InputResponses;
import io.airlift.mcp.model.JsonRpcErrorDetail;
import io.airlift.mcp.model.JsonRpcResponse;
import io.airlift.mcp.model.ListRootsRequest;
import io.airlift.mcp.model.ListRootsResult;
import io.airlift.mcp.model.Result;
import io.airlift.mcp.model.Root;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import java.util.function.BiFunction;

import static io.airlift.mcp.model.JsonRpcErrorCode.INTERNAL_ERROR;
import static io.airlift.mcp.model.JsonRpcErrorCode.REQUEST_TIMEOUT;

class MrtrEmulator
{
    private static final Logger log = Logger.get(MrtrEmulator.class);

    private final Duration streamingTimeout;
    private final Duration streamingPingThreshold;

    @Inject
    MrtrEmulator(McpConfig mcpConfig)
    {
        streamingTimeout = mcpConfig.getEventStreamingTimeout().toJavaTime();
        streamingPingThreshold = mcpConfig.getEventStreamingPingThreshold().toJavaTime();
    }

    interface ErrorFactory<T extends InputResponsable<T>, R extends Result>
    {
        R createErrorResponse(RequestContextImpl requestContext, JsonRpcErrorDetail errorDetail, T request);
    }

    <T extends InputResponsable<T>, R extends Result> R emulateMrtr(RequestContextImpl requestContext, T request, ErrorFactory<T, R> errorFactory, BiFunction<RequestContextImpl, T, R> operation)
    {
        R operationResponse;

        Stopwatch stopwatch = Stopwatch.createStarted();

        while (true) {
            operationResponse = operation.apply(requestContext, request);
            if (!(operationResponse instanceof InputRequests(var inputRequests, var requestState, _))) {
                break;
            }

            Map<String, InputResponse> inputResponses = new HashMap<>();
            for (Map.Entry<String, InputRequest> entry : inputRequests.entrySet()) {
                String key = entry.getKey();
                InputRequest inputRequest = entry.getValue();

                try {
                    Duration elapsed = stopwatch.elapsed();
                    Duration thisTimeout = streamingTimeout.minus(elapsed);
                    if (thisTimeout.isNegative()) {
                        return timeout(requestContext, request, errorFactory, key);
                    }

                    JsonRpcResponse<?> response;
                    if (inputRequest instanceof ListRootsRequest) {
                        List<Root> roots = requestContext.requestRoots(thisTimeout, streamingPingThreshold);
                        response = new JsonRpcResponse<>("", Optional.empty(), Optional.of(new ListRootsResult(roots, Optional.empty())));
                    }
                    else {
                        response = requestContext.serverToClientRequest(inputRequest.methodName(), inputRequest, inputRequest.responseType(), thisTimeout, streamingPingThreshold);
                    }

                    if (response.error().isPresent()) {
                        JsonRpcErrorDetail errorDetail = response.error().orElseThrow();
                        return errorFactory.createErrorResponse(requestContext, errorDetail, request);
                    }
                    else {
                        Object result = response.result().orElseThrow(() -> new IllegalStateException("Missing result in response for input request: " + key));
                        inputResponses.put(key, inputRequest.responseType().cast(result));
                    }
                }
                catch (InterruptedException _) {
                    Thread.currentThread().interrupt();

                    log.warn("Interrupted while waiting for input request: %s", key);
                    return errorFactory.createErrorResponse(requestContext, new JsonRpcErrorDetail(INTERNAL_ERROR, "Interrupted while waiting for input request: " + key), request);
                }
                catch (TimeoutException _) {
                    log.warn("Timeout while waiting for input request: %s", key);
                    return timeout(requestContext, request, errorFactory, key);
                }
            }

            request = request.withInputResponses(new InputResponses(inputResponses, requestState));
        }

        return operationResponse;
    }

    private static <T extends InputResponsable<T>, R extends Result> R timeout(RequestContextImpl requestContext, T request, ErrorFactory<T, R> errorFactory, String key)
    {
        return errorFactory.createErrorResponse(requestContext, new JsonRpcErrorDetail(REQUEST_TIMEOUT, "Timeout waiting for input request: " + key), request);
    }
}
