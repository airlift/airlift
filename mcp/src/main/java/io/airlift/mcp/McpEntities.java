package io.airlift.mcp;

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
    List<Tool> tools();

    List<Prompt> prompts();

    List<Resource> resources();

    List<ResourceTemplate> resourceTemplates();

    List<CompleteReference> completions();

    Optional<List<ResourceContents>> readResourceContents(McpRequestContext requestContext, ReadResourceRequest readResourceRequest);
}
