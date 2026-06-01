package io.airlift.mcp.operations.legacy;

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import io.airlift.mcp.McpClientException;
import io.airlift.mcp.McpConfig;
import io.airlift.mcp.McpEntities;
import io.airlift.mcp.handler.PromptEntry;
import io.airlift.mcp.handler.ToolEntry;
import io.airlift.mcp.model.CallToolRequest;
import io.airlift.mcp.model.CallToolResult;
import io.airlift.mcp.model.CompleteRequest;
import io.airlift.mcp.model.CompleteResult;
import io.airlift.mcp.model.Content.TextContent;
import io.airlift.mcp.model.GetPromptRequest;
import io.airlift.mcp.model.GetPromptResult;
import io.airlift.mcp.model.ListPromptsResult;
import io.airlift.mcp.model.ListRequest;
import io.airlift.mcp.model.ListResourceTemplatesResult;
import io.airlift.mcp.model.ListResourcesResult;
import io.airlift.mcp.model.ListToolsResult;
import io.airlift.mcp.model.Meta;
import io.airlift.mcp.model.OptionalBoolean;
import io.airlift.mcp.model.Prompt;
import io.airlift.mcp.model.Protocol;
import io.airlift.mcp.model.ReadResourceRequest;
import io.airlift.mcp.model.ReadResourceResult;
import io.airlift.mcp.model.Resource;
import io.airlift.mcp.model.ResourceContents;
import io.airlift.mcp.model.ResourceTemplate;
import io.airlift.mcp.model.Tool;
import io.airlift.mcp.operations.PaginationUtil;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static io.airlift.http.server.tracing.TracingServletFilter.updateRequestSpan;
import static io.airlift.mcp.McpException.exception;
import static io.airlift.mcp.model.JsonRpcErrorCode.INVALID_PARAMS;
import static io.airlift.mcp.model.JsonRpcErrorCode.RESOURCE_NOT_FOUND;
import static io.airlift.mcp.model.Protocol.PROTOCOL_MCP_2025_06_18;
import static io.opentelemetry.semconv.incubating.McpIncubatingAttributes.MCP_RESOURCE_URI;
import static java.util.Objects.requireNonNull;

public class OperationsCommon
{
    private final McpEntities entities;
    private final PaginationUtil paginationUtil;

    @Inject
    OperationsCommon(McpEntities entities, McpConfig mcpConfig)
    {
        this.entities = requireNonNull(entities, "entities is null");

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

    CallToolResult callTool(LegacyRequestContextImpl requestContext, CallToolRequest callToolRequest)
    {
        entities.validateToolAllowed(requestContext, callToolRequest.name());

        ToolEntry toolEntry = entities.toolEntry(requestContext, callToolRequest.name())
                .orElseThrow(() -> exception(INVALID_PARAMS, "Tool not found: " + callToolRequest.name()));

        try {
            return toolEntry.toolHandler().callTool(requestContext.withProgressToken(progressToken(callToolRequest)), callToolRequest);
        }
        catch (McpClientException mcpClientException) {
            return new CallToolResult(ImmutableList.of(new TextContent(mcpClientException.unwrap().errorDetail().message())), Optional.empty(), true, Optional.empty());
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

        return promptEntry.promptHandler().getPrompt(requestContext.withProgressToken(progressToken(getPromptRequest)), getPromptRequest);
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

    Object readResources(LegacyRequestContextImpl requestContext, ReadResourceRequest readResourceRequest)
    {
        updateRequestSpan(requestContext.request(), span -> span.setAttribute(MCP_RESOURCE_URI, readResourceRequest.uri()));

        List<ResourceContents> resourceContents = entities.readResourceContents(requestContext.withProgressToken(progressToken(readResourceRequest)), readResourceRequest)
                .orElseThrow(() -> exception(RESOURCE_NOT_FOUND, "Resource not found: " + readResourceRequest.uri()));

        return new ReadResourceResult(resourceContents);
    }

    CompleteResult completionComplete(LegacyRequestContextImpl requestContext, CompleteRequest completeRequest)
    {
        return entities.completionEntry(requestContext, completeRequest.ref())
                .map(completionEntry -> completionEntry.handler().complete(requestContext.withProgressToken(progressToken(completeRequest)), completeRequest))
                .orElseGet(() -> new CompleteResult(new CompleteResult.CompleteCompletion(ImmutableList.of(), OptionalInt.empty(), OptionalBoolean.UNDEFINED)));
    }

    private Optional<Object> progressToken(Meta meta)
    {
        return meta.meta().flatMap(m -> Optional.ofNullable(m.get("progressToken")));
    }
}
