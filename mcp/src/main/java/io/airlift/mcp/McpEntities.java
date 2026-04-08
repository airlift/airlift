package io.airlift.mcp;

import io.airlift.mcp.handler.CompletionEntry;
import io.airlift.mcp.handler.CompletionHandler;
import io.airlift.mcp.handler.PromptEntry;
import io.airlift.mcp.handler.PromptHandler;
import io.airlift.mcp.handler.ResourceEntry;
import io.airlift.mcp.handler.ResourceHandler;
import io.airlift.mcp.handler.ResourceTemplateHandler;
import io.airlift.mcp.handler.ToolEntry;
import io.airlift.mcp.handler.ToolHandler;
import io.airlift.mcp.model.CompleteReference;
import io.airlift.mcp.model.Prompt;
import io.airlift.mcp.model.ReadResourceRequest;
import io.airlift.mcp.model.ReadResourceResult;
import io.airlift.mcp.model.Resource;
import io.airlift.mcp.model.ResourceTemplate;
import io.airlift.mcp.model.Tool;

import java.util.List;
import java.util.Optional;

public interface McpEntities
{
    List<Tool> tools(McpRequestContext requestContext);

    Optional<ToolEntry> toolEntry(McpRequestContext requestContext, String toolName);

    void validateToolAllowed(McpRequestContext requestContext, String toolName);

    List<Prompt> prompts(McpRequestContext requestContext);

    Optional<PromptEntry> promptEntry(McpRequestContext requestContext, String promptName);

    void validatePromptAllowed(McpRequestContext requestContext, String promptName);

    List<Resource> resources(McpRequestContext requestContext);

    Optional<ResourceEntry> resourceEntry(McpRequestContext requestContext, String uri);

    void validateResourceAllowed(McpRequestContext requestContext, String uri);

    List<ResourceTemplate> resourceTemplates(McpRequestContext requestContext);

    List<CompleteReference> completions(McpRequestContext requestContext);

    Optional<CompletionEntry> completionEntry(McpRequestContext requestContext, CompleteReference ref);

    Optional<ReadResourceResult> readResourceContents(McpRequestContext requestContext, ReadResourceRequest readResourceRequest);

    void addTool(Tool tool, ToolHandler toolHandler);

    void removeTool(String toolName);

    void addPrompt(Prompt prompt, PromptHandler promptHandler);

    void removePrompt(String promptName);

    void addResource(Resource resource, ResourceHandler handler);

    void removeResource(String resourceUri);

    void addResourceTemplate(ResourceTemplate resourceTemplate, ResourceTemplateHandler handler);

    void removeResourceTemplate(String uriTemplate);

    void addCompletion(CompleteReference reference, CompletionHandler handler);

    void removeCompletion(CompleteReference reference);
}
