package io.airlift.mcp.operations.legacy;

import com.google.inject.Inject;
import io.airlift.mcp.McpClientException;
import io.airlift.mcp.McpConfig;
import io.airlift.mcp.McpEntities;
import io.airlift.mcp.McpTaskController;
import io.airlift.mcp.handler.PromptEntry;
import io.airlift.mcp.handler.PromptHandler;
import io.airlift.mcp.handler.ToolEntry;
import io.airlift.mcp.model.CallToolRequest;
import io.airlift.mcp.model.CallToolResult;
import io.airlift.mcp.model.CompleteRequest;
import io.airlift.mcp.model.CompleteResult;
import io.airlift.mcp.model.CompleteTaskResult;
import io.airlift.mcp.model.CreateTaskResult;
import io.airlift.mcp.model.EmptyResult;
import io.airlift.mcp.model.GetPromptRequest;
import io.airlift.mcp.model.GetPromptResult;
import io.airlift.mcp.model.InputRequiredTaskResult;
import io.airlift.mcp.model.JsonRpcErrorDetail;
import io.airlift.mcp.model.ListPromptsResult;
import io.airlift.mcp.model.ListRequest;
import io.airlift.mcp.model.ListResourceTemplatesResult;
import io.airlift.mcp.model.ListResourcesResult;
import io.airlift.mcp.model.ListToolsResult;
import io.airlift.mcp.model.Meta;
import io.airlift.mcp.model.Prompt;
import io.airlift.mcp.model.Protocol;
import io.airlift.mcp.model.ReadResourceRequest;
import io.airlift.mcp.model.ReadResourceResult;
import io.airlift.mcp.model.Resource;
import io.airlift.mcp.model.ResourceTemplate;
import io.airlift.mcp.model.Result;
import io.airlift.mcp.model.Tool;
import io.airlift.mcp.operations.OperationsImpl;
import io.airlift.mcp.operations.PaginationUtil;

import java.util.List;
import java.util.Optional;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static io.airlift.mcp.McpException.exception;
import static io.airlift.mcp.McpTaskController.ErrorState.FAILED;
import static io.airlift.mcp.model.Constants.METADATA_PROGRESS_TOKEN;
import static io.airlift.mcp.model.JsonRpcErrorCode.INVALID_PARAMS;
import static io.airlift.mcp.model.JsonRpcErrorCode.UNSUPPORTED_PROTOCOL;
import static io.airlift.mcp.model.Protocol.PROTOCOL_MCP_2025_06_18;
import static java.util.Objects.requireNonNull;

public class OperationsCommon
{
    private final McpEntities entities;
    private final PaginationUtil paginationUtil;
    private final MrtrEmulator mrtrEmulator;
    private final Optional<McpTaskController> taskController;

    @Inject
    OperationsCommon(McpEntities entities, McpConfig mcpConfig, MrtrEmulator mrtrEmulator, Optional<McpTaskController> taskController)
    {
        this.entities = requireNonNull(entities, "entities is null");
        this.mrtrEmulator = requireNonNull(mrtrEmulator, "mrtrEmulator is null");
        this.taskController = requireNonNull(taskController, "taskController is null");

        paginationUtil = new PaginationUtil(mcpConfig);
    }

    static boolean supportsIcons(Protocol protocol)
    {
        return protocol != PROTOCOL_MCP_2025_06_18;
    }

    ListToolsResult listTools(LegacyRequestContextImpl requestContext, ListRequest listRequest)
    {
        List<Tool> localTools = entities.tools(requestContext)
                .stream()
                .map(tool -> supportsIcons(requestContext.protocol()) ? tool : tool.withoutIcons())
                .collect(toImmutableList());
        return paginationUtil.paginate(listRequest, localTools, Tool::name, ListToolsResult::new);
    }

    Result callTool(LegacyRequestContextImpl requestContext, CallToolRequest callToolRequest)
    {
        entities.validateToolAllowed(requestContext, callToolRequest.name());

        ToolEntry toolEntry = entities.toolEntry(requestContext, callToolRequest.name())
                .orElseThrow(() -> exception(INVALID_PARAMS, "Tool not found: " + callToolRequest.name()));

        try {
            LegacyRequestContextImpl processTokenRequestContext = requestContext.withProgressToken(progressToken(callToolRequest));

            CallToolResult callToolResult = unwrap(toolEntry.toolHandler().callTool(processTokenRequestContext, callToolRequest));
            return mrtrEmulator.emulate(processTokenRequestContext, callToolResult, (requestState, inputResponses) -> {
                CallToolRequest adjustedCallToolRequest = callToolRequest.withInputResponses(requestState, inputResponses);
                return unwrap(toolEntry.toolHandler().callTool(processTokenRequestContext, adjustedCallToolRequest));
            });
        }
        catch (McpClientException mcpClientException) {
            return CallToolResult.forError(mcpClientException);
        }
    }

    ListPromptsResult listPrompts(LegacyRequestContextImpl requestContext, ListRequest listRequest)
    {
        List<Prompt> localPrompts = entities.prompts(requestContext)
                .stream()
                .map(prompt -> supportsIcons(requestContext.protocol()) ? prompt : prompt.withoutIcons())
                .collect(toImmutableList());
        return paginationUtil.paginate(listRequest, localPrompts, Prompt::name, ListPromptsResult::new);
    }

    GetPromptResult getPrompt(LegacyRequestContextImpl requestContext, GetPromptRequest getPromptRequest)
    {
        entities.validatePromptAllowed(requestContext, getPromptRequest.name());

        PromptEntry promptEntry = entities.promptEntry(requestContext, getPromptRequest.name())
                .orElseThrow(() -> exception(INVALID_PARAMS, "Prompt not found: " + getPromptRequest.name()));

        PromptHandler promptHandler = promptEntry.promptHandler();
        LegacyRequestContextImpl processTokenRequestContext = requestContext.withProgressToken(progressToken(getPromptRequest));
        GetPromptResult getPromptResult = promptHandler.getPrompt(processTokenRequestContext, getPromptRequest);
        return mrtrEmulator.emulate(processTokenRequestContext, getPromptResult, (requestState, inputResponses) -> {
            GetPromptRequest adjustedGetPromptRequest = getPromptRequest.withInputResponses(requestState, inputResponses);
            return promptHandler.getPrompt(processTokenRequestContext, adjustedGetPromptRequest);
        });
    }

    ListResourcesResult listResources(LegacyRequestContextImpl requestContext, ListRequest listRequest)
    {
        List<Resource> localResources = entities.resources(requestContext)
                .stream()
                .map(resource -> supportsIcons(requestContext.protocol()) ? resource : resource.withoutIcons())
                .collect(toImmutableList());
        return paginationUtil.paginate(listRequest, localResources, Resource::name, ListResourcesResult::new);
    }

    ListResourceTemplatesResult listResourceTemplates(LegacyRequestContextImpl requestContext, ListRequest listRequest)
    {
        List<ResourceTemplate> localResourceTemplates = entities.resourceTemplates(requestContext)
                .stream()
                .map(resourceTemplate -> supportsIcons(requestContext.protocol()) ? resourceTemplate : resourceTemplate.withoutIcons())
                .collect(toImmutableList());
        return paginationUtil.paginate(listRequest, localResourceTemplates, ResourceTemplate::name, ListResourceTemplatesResult::new);
    }

    ReadResourceResult readResources(LegacyRequestContextImpl requestContext, ReadResourceRequest readResourceRequest)
    {
        LegacyRequestContextImpl processTokenRequestContext = requestContext.withProgressToken(progressToken(readResourceRequest));
        ReadResourceResult readResourceResult = OperationsImpl.readResources(entities, processTokenRequestContext, readResourceRequest);
        return mrtrEmulator.emulate(processTokenRequestContext, readResourceResult, (requestState, inputResponses) -> {
            ReadResourceRequest adjustedReadResourceRequest = readResourceRequest.withInputResponses(requestState, inputResponses);
            return OperationsImpl.readResources(entities, processTokenRequestContext, adjustedReadResourceRequest);
        });
    }

    CompleteResult completionComplete(LegacyRequestContextImpl requestContext, CompleteRequest completeRequest)
    {
        return entities.completionEntry(requestContext, completeRequest.ref())
                .map(completionEntry -> completionEntry.handler().complete(requestContext.withProgressToken(progressToken(completeRequest)), completeRequest))
                .orElseGet(CompleteResult::empty);
    }

    static Optional<Object> progressToken(Meta meta)
    {
        return meta.meta().flatMap(m -> Optional.ofNullable(m.get(METADATA_PROGRESS_TOKEN)));
    }

    private CallToolResult unwrap(Result result)
    {
        return switch (result) {
            case CallToolResult callToolResult -> callToolResult;
            case EmptyResult _ -> throw new IllegalStateException("Tasks are not supported at this protocol version");
            case InputRequiredTaskResult inputRequiredTaskResult -> throw taskException(inputRequiredTaskResult.task().taskId());
            case CreateTaskResult createTaskResult -> throw taskException(createTaskResult.task().taskId());
            case CompleteTaskResult completeTaskResult -> throw taskException(completeTaskResult.task().taskId());
        };
    }

    private RuntimeException taskException(String taskId)
    {
        taskController.ifPresent(controller -> controller.setErrorState(taskId, FAILED, Optional.of(new JsonRpcErrorDetail(UNSUPPORTED_PROTOCOL, "Tasks are not supported at this protocol version"))));
        return new IllegalStateException("Tasks are not supported at this protocol version");
    }
}
