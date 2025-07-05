package io.airlift.mcp.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import io.airlift.jsonrpc.JsonRpc;
import io.airlift.log.Logger;
import io.airlift.mcp.McpException;
import io.airlift.mcp.McpServer;
import io.airlift.mcp.model.CallToolRequest;
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
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Request;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;

import java.util.Map;

import static io.airlift.mcp.model.Constants.METHOD_CALL_TOOL;
import static io.airlift.mcp.model.Constants.METHOD_COMPLETION_COMPLETE;
import static io.airlift.mcp.model.Constants.METHOD_GET_PROMPT;
import static io.airlift.mcp.model.Constants.METHOD_INITIALIZE;
import static io.airlift.mcp.model.Constants.METHOD_PING;
import static io.airlift.mcp.model.Constants.METHOD_PROMPTS_LIST;
import static io.airlift.mcp.model.Constants.METHOD_READ_RESOURCES;
import static io.airlift.mcp.model.Constants.METHOD_RESOURCES_LIST;
import static io.airlift.mcp.model.Constants.METHOD_RESOURCES_TEMPLATES_LIST;
import static io.airlift.mcp.model.Constants.METHOD_TOOLS_LIST;
import static io.airlift.mcp.model.Constants.NOTIFICATION_INITIALIZED;
import static jakarta.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static jakarta.ws.rs.core.MediaType.SERVER_SENT_EVENTS;
import static java.util.Objects.requireNonNull;

public class InternalRpcMethods
{
    private static final Logger log = Logger.get(InternalRpcMethods.class);

    private final McpServer mcpServer;
    private final ObjectMapper objectMapper;

    @Inject
    public InternalRpcMethods(McpServer mcpServer, ObjectMapper objectMapper)
    {
        this.mcpServer = requireNonNull(mcpServer, "mcpServer is null");
        this.objectMapper = requireNonNull(objectMapper, "objectMapper is null");
    }

    @JsonRpc(METHOD_PING)
    public Map<String, String> ping()
    {
        log.debug("Received ping request");

        return ImmutableMap.of();
    }

    @JsonRpc(METHOD_INITIALIZE)
    public InitializeResult initialize(@Context Request request, InitializeRequest initializeRequest)
    {
        log.debug("Received initialize request: %s", initializeRequest);

        return mcpServer.initialize(initializeRequest);
    }

    @JsonRpc(NOTIFICATION_INITIALIZED)
    public Response notificationsInitialized()
    {
        log.debug("Received notification that notifications have been initialized");

        return Response.accepted().build();
    }

    @JsonRpc(METHOD_TOOLS_LIST)
    public ListToolsResponse listTools()
    {
        log.debug("Received list tools request");

        return mcpServer.listTools();
    }

    @JsonRpc(METHOD_CALL_TOOL)
    @Produces({SERVER_SENT_EVENTS, APPLICATION_JSON})
    public Response callTool(@Context Request request, CallToolRequest callToolRequest)
    {
        log.debug("Received call tool request: %s", callToolRequest);

        return asStreamingOutput(request, callToolRequest,
                notifier -> mcpServer.callTool(request, notifier, callToolRequest));
    }

    @JsonRpc(METHOD_PROMPTS_LIST)
    public ListPromptsResult listPrompts()
    {
        log.debug("Received list prompts request");

        return mcpServer.listPrompts();
    }

    @JsonRpc(METHOD_GET_PROMPT)
    @Produces({SERVER_SENT_EVENTS, APPLICATION_JSON})
    public Response getPrompt(@Context Request request, GetPromptRequest getPromptRequest)
    {
        log.debug("Received get prompt request: %s", getPromptRequest);

        return asStreamingOutput(request, getPromptRequest,
                notifier -> mcpServer.getPrompt(request, notifier, getPromptRequest));
    }

    @JsonRpc(METHOD_RESOURCES_LIST)
    public ListResourcesResult listResources()
    {
        log.debug("Received list resources request");

        return mcpServer.listResources();
    }

    @JsonRpc(METHOD_RESOURCES_TEMPLATES_LIST)
    public ListResourceTemplatesResult listResourceTemplates(@Context Request request)
    {
        log.debug("Received list resources templates request");

        return mcpServer.listResourceTemplates();
    }

    @JsonRpc(METHOD_READ_RESOURCES)
    @Produces({SERVER_SENT_EVENTS, APPLICATION_JSON})
    public Response readResources(@Context Request request, ReadResourceRequest readResourceRequest)
    {
        log.debug("Received read resources request: %s", readResourceRequest);

        return asStreamingOutput(request, readResourceRequest,
                notifier -> mcpServer.readResources(request, notifier, readResourceRequest));
    }

    @JsonRpc(METHOD_COMPLETION_COMPLETE)
    @Produces({SERVER_SENT_EVENTS, APPLICATION_JSON})
    public Response completeCompletion(@Context Request request, CompletionRequest completionRequest)
    {
        log.debug("Received completion request: %s", completionRequest);

        return asStreamingOutput(request, completionRequest,
                notifier -> mcpServer.completeCompletion(request, notifier, completionRequest));
    }

    private interface ResultSupplier
    {
        Object apply(InternalNotifier internalNotifier)
                throws McpException;
    }

    private Response asStreamingOutput(Request request, Meta meta, ResultSupplier resultSupplier)
    {
        StreamingOutput streamingOutput = output -> {
            try (output) {
                InternalNotifier internalNotifier = new InternalNotifier(request, objectMapper, meta, output);
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
            }
        };

        BufferDefeatingStreamingOutput wrapped = new BufferDefeatingStreamingOutput(streamingOutput);
        return Response.ok(wrapped)
                .header(CONTENT_TYPE, SERVER_SENT_EVENTS)
                .build();
    }
}
