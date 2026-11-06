package io.airlift.mcp.client;

import io.airlift.mcp.model.ListPromptsResult;
import io.airlift.mcp.model.ListResourceTemplatesResult;
import io.airlift.mcp.model.ListResourcesResult;
import io.airlift.mcp.model.ListToolsResult;
import io.airlift.mcp.model.Prompt;
import io.airlift.mcp.model.Resource;
import io.airlift.mcp.model.ResourceTemplate;
import io.airlift.mcp.model.Tool;

import java.util.stream.Stream;

import static io.airlift.mcp.client.internal.InternalPagedStream.pagedStream;

public final class McpStreamUtil
{
    private McpStreamUtil() {}

    public static Stream<Tool> streamTools(McpConnection connection)
    {
        return pagedStream(connection, McpConnection::listTools, ListToolsResult::tools);
    }

    public static Stream<Prompt> streamPrompts(McpConnection connection)
    {
        return pagedStream(connection, McpConnection::listPrompts, ListPromptsResult::prompts);
    }

    public static Stream<Resource> streamResources(McpConnection connection)
    {
        return pagedStream(connection, McpConnection::listResources, ListResourcesResult::resources);
    }

    public static Stream<ResourceTemplate> streamResourceTemplates(McpConnection connection)
    {
        return pagedStream(connection, McpConnection::listResourceTemplates, ListResourceTemplatesResult::resourceTemplates);
    }
}
