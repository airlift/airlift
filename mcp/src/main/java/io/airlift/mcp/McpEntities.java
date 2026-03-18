package io.airlift.mcp;

import io.airlift.mcp.McpIdentity.Authenticated;
import io.airlift.mcp.handler.CompletionHandler;
import io.airlift.mcp.handler.PromptHandler;
import io.airlift.mcp.handler.ResourceHandler;
import io.airlift.mcp.handler.ResourceTemplateHandler;
import io.airlift.mcp.handler.ToolHandler;
import io.airlift.mcp.model.CompleteReference;
import io.airlift.mcp.model.Prompt;
import io.airlift.mcp.model.ReadResourceRequest;
import io.airlift.mcp.model.Resource;
import io.airlift.mcp.model.ResourceContents;
import io.airlift.mcp.model.ResourceTemplate;
import io.airlift.mcp.model.Tool;

import java.util.List;
import java.util.Optional;

public interface McpEntities
{
    List<Tool> tools(Optional<Authenticated<?>> identity);

    List<Prompt> prompts(Optional<Authenticated<?>> identity);

    List<Resource> resources(Optional<Authenticated<?>> identity);

    List<ResourceTemplate> resourceTemplates(Optional<Authenticated<?>> identity);

    List<CompleteReference> completions(Optional<Authenticated<?>> identity);

    Optional<List<ResourceContents>> readResourceContents(Optional<Authenticated<?>> identity, McpRequestContext requestContext, ReadResourceRequest readResourceRequest);

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
