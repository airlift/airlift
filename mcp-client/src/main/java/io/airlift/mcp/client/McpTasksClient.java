package io.airlift.mcp.client;

import java.net.URI;

public interface McpTasksClient
{
    McpTasksConnection connect(URI uri);
}
