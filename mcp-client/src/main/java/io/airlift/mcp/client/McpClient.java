package io.airlift.mcp.client;

import io.airlift.http.client.HttpClient;
import io.airlift.mcp.client.internal.InternalClient;

import java.net.URI;

public interface McpClient
{
    static McpClient mcpClient(HttpClient httpClient)
    {
        return new InternalClient(httpClient);
    }

    McpConnection connect(URI uri);

    McpTasksClient withTasks();

    HttpClient httpClient();

    <V> V setting(McpClientSetting<V> setting);

    <V> McpClient withSetting(McpClientSetting<V> setting, V value);

    <V> V defaultConnectionSetting(McpConnectionSetting<V> setting);

    <V> McpClient withDefaultConnectionSetting(McpConnectionSetting<V> setting, V value);
}
