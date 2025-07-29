package io.airlift.mcp.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import io.airlift.jsonrpc.JsonRpc;
import io.airlift.jsonrpc.JsonRpcResult;
import io.airlift.jsonrpc.binding.InternalRpcFilter;
import io.airlift.jsonrpc.model.JsonRpcResponse;
import io.airlift.log.Logger;
import io.airlift.mcp.McpException;
import io.airlift.mcp.McpServer;
import io.airlift.mcp.model.CallToolRequest;
import io.airlift.mcp.model.CancellationRequest;
import io.airlift.mcp.model.CompletionRequest;
import io.airlift.mcp.model.GetPromptRequest;
import io.airlift.mcp.model.InitializeRequest;
import io.airlift.mcp.model.InitializeResult;
import io.airlift.mcp.model.ListPromptsResult;
import io.airlift.mcp.model.ListResourceTemplatesResult;
import io.airlift.mcp.model.ListResourcesResult;
import io.airlift.mcp.model.ListToolsResponse;
import io.airlift.mcp.model.Meta;
import io.airlift.mcp.model.ReadResourceRequest;
import io.airlift.mcp.model.RootsResponse;
import io.airlift.mcp.model.SetLoggingLevelRequest;
import io.airlift.mcp.model.SubscribeResourceRequest;
import io.airlift.mcp.session.RequestState;
import io.airlift.mcp.session.SessionController;
import io.airlift.mcp.session.SessionId;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Request;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;

import java.util.Map;
import java.util.Optional;

import static com.google.common.base.MoreObjects.firstNonNull;
import static io.airlift.jsonrpc.model.JsonRpcErrorCode.INVALID_REQUEST;
import static io.airlift.mcp.internal.InternalSessionResource.SERVER_TO_CLIENT_ID_SIGNIFIER;
import static io.airlift.mcp.model.Constants.METHOD_CALL_TOOL;
import static io.airlift.mcp.model.Constants.METHOD_COMPLETION_COMPLETE;
import static io.airlift.mcp.model.Constants.METHOD_GET_PROMPT;
import static io.airlift.mcp.model.Constants.METHOD_INITIALIZE;
import static io.airlift.mcp.model.Constants.METHOD_PING;
import static io.airlift.mcp.model.Constants.METHOD_PROMPTS_LIST;
import static io.airlift.mcp.model.Constants.METHOD_READ_RESOURCES;
import static io.airlift.mcp.model.Constants.METHOD_RESOURCES_LIST;
import static io.airlift.mcp.model.Constants.METHOD_RESOURCES_SUBSCRIBE;
import static io.airlift.mcp.model.Constants.METHOD_RESOURCES_TEMPLATES_LIST;
import static io.airlift.mcp.model.Constants.METHOD_RESOURCES_UNSUBSCRIBE;
import static io.airlift.mcp.model.Constants.METHOD_ROOTS_LIST;
import static io.airlift.mcp.model.Constants.METHOD_SET_LOGGING_LEVEL;
import static io.airlift.mcp.model.Constants.METHOD_TOOLS_LIST;
import static io.airlift.mcp.model.Constants.NOTIFICATION_CANCELLED;
import static io.airlift.mcp.model.Constants.NOTIFICATION_INITIALIZED;
import static io.airlift.mcp.model.Constants.NOTIFICATION_ROOTS_CHANGED;
import static io.airlift.mcp.model.Constants.SESSION_HEADER;
import static io.airlift.mcp.session.SubscribeType.SUBSCRIBE;
import static io.airlift.mcp.session.SubscribeType.UNSUBSCRIBE;
import static jakarta.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static jakarta.ws.rs.core.MediaType.SERVER_SENT_EVENTS;
import static java.util.Objects.requireNonNull;

public class InternalRpcMethods
{
    private static final Logger log = Logger.get(InternalRpcMethods.class);

    private final McpServer mcpServer;
    private final ObjectMapper objectMapper;
    private final Optional<SessionController> sessionController;

    @Inject
    public InternalRpcMethods(McpServer mcpServer, ObjectMapper objectMapper, Optional<SessionController> sessionController)
    {
        this.mcpServer = requireNonNull(mcpServer, "mcpServer is null");
        this.objectMapper = requireNonNull(objectMapper, "objectMapper is null");
        this.sessionController = requireNonNull(sessionController, "sessionController is null");
    }

    @JsonRpc(METHOD_PING)
    public Map<String, String> ping()
    {
        log.debug("Received ping request");

        return ImmutableMap.of();
    }

    @SuppressWarnings("SwitchStatementWithTooFewBranches")
    @JsonRpcResult("internal-results")
    public Response resultHandler(@Context SessionId sessionId, JsonRpcResponse<Map<String, Object>> response)
    {
        log.debug("Received server-to-client response: %s", response);

        sessionController.ifPresent(controller -> {
            String resultId = String.valueOf(firstNonNull(response.id(), ""));

            switch (resultId) {
                case METHOD_ROOTS_LIST -> response.result().ifPresent(result -> {
                    RootsResponse rootsResponse = objectMapper.convertValue(result, RootsResponse.class);
                    controller.acceptRoots(sessionId, rootsResponse.roots());
                });

                default -> {
                    if (resultId.startsWith(SERVER_TO_CLIENT_ID_SIGNIFIER)) {
                        String unwrappedId = resultId.substring(SERVER_TO_CLIENT_ID_SIGNIFIER.length());
                        JsonRpcResponse<Map<String, Object>> appliedResult = new JsonRpcResponse<>(unwrappedId, response.error(), response.result());
                        controller.acceptServerToClientResponse(sessionId, appliedResult);
                    }
                }
            }
        });

        return Response.accepted().build();
    }

    @JsonRpc(METHOD_INITIALIZE)
    public Response initialize(@Context Request request, InitializeRequest initializeRequest)
    {
        log.debug("Received initialize request: %s", initializeRequest);

        InitializeResult initializeResult = mcpServer.initialize(initializeRequest);
        Response.ResponseBuilder responseBuilder = Response.ok(initializeResult);
        sessionController.ifPresent(controller -> {
            SessionId sessionId = controller.createSession(initializeRequest);
            log.debug("Creating session for initialize request: %s, sessionId: %s", initializeRequest, sessionId);

            responseBuilder.header(SESSION_HEADER, sessionId.asString());
        });
        return responseBuilder.build();
    }

    @JsonRpc(NOTIFICATION_INITIALIZED)
    public Response notificationInitialized(@Context SessionId sessionId)
    {
        log.debug("Received notification that notifications have been initialized. SessionId: %s", sessionId);

        return Response.accepted().build();
    }

    @JsonRpc(NOTIFICATION_CANCELLED)
    public Response notificationCancelled(@Context Request request, @Context SessionId sessionId, CancellationRequest cancellationRequest)
    {
        log.debug("Received request cancellation notification. SessionId: %s, RequestId: %s", sessionId, cancellationRequest.requestId());

        sessionController.ifPresent(controller -> controller.acceptRequestState(sessionId, String.valueOf(cancellationRequest.requestId()), RequestState.CANCELLATION_REQUESTED));

        return Response.accepted().build();
    }

    @JsonRpc(NOTIFICATION_ROOTS_CHANGED)
    public Response notificationRootsChanged(@Context SessionId sessionId)
    {
        log.debug("Received notification that roosts have changed. SessionId: %s", sessionId);

        sessionController.ifPresent(controller -> controller.acceptRootsChanged(sessionId));

        return Response.accepted().build();
    }

    @JsonRpc(METHOD_TOOLS_LIST)
    public ListToolsResponse listTools(@Context SessionId sessionId)
    {
        log.debug("Received list tools request. SessionId: %s", sessionId);

        return mcpServer.listTools();
    }

    @JsonRpc(METHOD_CALL_TOOL)
    @Produces({SERVER_SENT_EVENTS, APPLICATION_JSON})
    public Response callTool(@Context Request request, @Context SessionId sessionId, CallToolRequest callToolRequest)
    {
        log.debug("Received call tool request: %s, sessionId: %s", callToolRequest, sessionId);

        return asStreamingOutput(request, sessionId, callToolRequest,
                notifier -> mcpServer.callTool(request, sessionId, notifier, callToolRequest));
    }

    @JsonRpc(METHOD_PROMPTS_LIST)
    public ListPromptsResult listPrompts(@Context SessionId sessionId)
    {
        log.debug("Received list prompts request. SessionId: %s", sessionId);

        return mcpServer.listPrompts();
    }

    @JsonRpc(METHOD_GET_PROMPT)
    @Produces({SERVER_SENT_EVENTS, APPLICATION_JSON})
    public Response getPrompt(@Context Request request, @Context SessionId sessionId, GetPromptRequest getPromptRequest)
    {
        log.debug("Received get prompt request: %s, sessionId: %s", getPromptRequest, sessionId);

        return asStreamingOutput(request, sessionId, getPromptRequest,
                notifier -> mcpServer.getPrompt(request, sessionId, notifier, getPromptRequest));
    }

    @JsonRpc(METHOD_RESOURCES_LIST)
    public ListResourcesResult listResources(@Context SessionId sessionId)
    {
        log.debug("Received list resources request. SessionId: %s", sessionId);

        return mcpServer.listResources();
    }

    @JsonRpc(METHOD_RESOURCES_TEMPLATES_LIST)
    public ListResourceTemplatesResult listResourceTemplates(@Context Request request, @Context SessionId sessionId)
    {
        log.debug("Received list resources templates request. SessionId: %s", sessionId);

        return mcpServer.listResourceTemplates();
    }

    @JsonRpc(METHOD_READ_RESOURCES)
    @Produces({SERVER_SENT_EVENTS, APPLICATION_JSON})
    public Response readResources(@Context Request request, @Context SessionId sessionId, ReadResourceRequest readResourceRequest)
    {
        log.debug("Received read resources request: %s, sessionId: %s", readResourceRequest, sessionId);

        return asStreamingOutput(request, sessionId, readResourceRequest,
                notifier -> mcpServer.readResources(request, sessionId, notifier, readResourceRequest));
    }

    @JsonRpc(METHOD_COMPLETION_COMPLETE)
    @Produces({SERVER_SENT_EVENTS, APPLICATION_JSON})
    public Response completeCompletion(@Context Request request, @Context SessionId sessionId, CompletionRequest completionRequest)
    {
        log.debug("Received completion request: %s, sessionId: %s", completionRequest, sessionId);

        return asStreamingOutput(request, sessionId, completionRequest,
                notifier -> mcpServer.completeCompletion(request, sessionId, notifier, completionRequest));
    }

    @JsonRpc(METHOD_SET_LOGGING_LEVEL)
    public Map<String, String> setLoggingLevel(@Context Request request, @Context SessionId sessionId, SetLoggingLevelRequest loggingLevelRequest)
    {
        log.debug("Received set logging level request: %s, sessionId: %s", loggingLevelRequest, sessionId);

        requireSessionController().setLoggingLevel(sessionId, loggingLevelRequest.level());

        return ImmutableMap.of();
    }

    @JsonRpc(METHOD_RESOURCES_SUBSCRIBE)
    public Map<String, String> subscribeToResource(@Context Request request, @Context SessionId sessionId, SubscribeResourceRequest subscribeResourceRequest)
    {
        log.debug("Received subscribe to resource request: %s, sessionId: %s", subscribeResourceRequest, sessionId);

        requireSessionController().changeResourceSubscription(sessionId, subscribeResourceRequest.uri(), SUBSCRIBE);

        return ImmutableMap.of();
    }

    @JsonRpc(METHOD_RESOURCES_UNSUBSCRIBE)
    public Map<String, String> unsubscribeToResource(@Context Request request, @Context SessionId sessionId, SubscribeResourceRequest subscribeResourceRequest)
    {
        log.debug("Received unsubscribe to resource request: %s, sessionId: %s", subscribeResourceRequest, sessionId);

        requireSessionController().changeResourceSubscription(sessionId, subscribeResourceRequest.uri(), UNSUBSCRIBE);

        return ImmutableMap.of();
    }

    static Optional<Object> currentRequestId(Request request)
    {
        return (request instanceof ContainerRequestContext containerRequestContext)
                ? InternalRpcFilter.requestId(containerRequestContext)
                : Optional.empty();
    }

    private SessionController requireSessionController()
    {
        return sessionController.orElseThrow(() -> McpException.exception(INVALID_REQUEST, "Sessions are not supported"));
    }

    private interface ResultSupplier
    {
        Object apply(InternalNotifier internalNotifier)
                throws McpException;
    }

    private Response asStreamingOutput(Request request, SessionId sessionId, Meta meta, ResultSupplier resultSupplier)
    {
        Optional<String> requestId = currentRequestId(request).map(String::valueOf);

        StreamingOutput streamingOutput = output -> {
            try (output) {
                sessionController.ifPresent(controller -> requestId.ifPresent(id -> controller.acceptRequestState(sessionId, id, RequestState.STARTED)));

                InternalNotifier internalNotifier = new InternalNotifier(request, sessionController, sessionId, objectMapper, meta, output);
                try {
                    Object result = resultSupplier.apply(internalNotifier);
                    internalNotifier.writeResult(result);
                }
                catch (McpException e) {
                    // debug on purpose. This exception will result in an error result.
                    // Normally, there's no need to log this, but it can be useful for debugging.
                    log.debug(e, "Error processing request");

                    internalNotifier.writeError(e.errorDetail());
                }
                finally {
                    sessionController.ifPresent(controller -> requestId.ifPresent(id -> controller.acceptRequestState(sessionId, id, RequestState.ENDED)));
                }
            }
        };

        BufferDefeatingStreamingOutput wrapped = new BufferDefeatingStreamingOutput(streamingOutput);
        return Response.ok(wrapped)
                .header(CONTENT_TYPE, SERVER_SENT_EVENTS)
                .build();
    }
}
