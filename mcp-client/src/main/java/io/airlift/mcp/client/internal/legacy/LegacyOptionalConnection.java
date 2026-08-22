package io.airlift.mcp.client.internal.legacy;

import io.airlift.mcp.client.McpConnectionSetting;
import io.airlift.mcp.client.McpTasksConnection;
import io.airlift.mcp.client.internal.InternalClient;
import io.airlift.mcp.client.internal.settings.SettingContainer;
import io.airlift.mcp.model.CallToolRequest;
import io.airlift.mcp.model.CallToolResult;
import io.airlift.mcp.model.CompleteRequest;
import io.airlift.mcp.model.CompleteResult;
import io.airlift.mcp.model.DiscoverResult;
import io.airlift.mcp.model.GetPromptRequest;
import io.airlift.mcp.model.GetPromptResult;
import io.airlift.mcp.model.GetTaskRequest;
import io.airlift.mcp.model.ListPromptsResult;
import io.airlift.mcp.model.ListResourceTemplatesResult;
import io.airlift.mcp.model.ListResourcesResult;
import io.airlift.mcp.model.ListToolsResult;
import io.airlift.mcp.model.Meta;
import io.airlift.mcp.model.ReadResourceRequest;
import io.airlift.mcp.model.ReadResourceResult;
import io.airlift.mcp.model.SubscriptionNotifications;
import io.airlift.mcp.model.Task;
import io.airlift.mcp.model.ToolResult;
import io.airlift.mcp.model.UpdateTaskRequest;

import java.net.URI;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static io.airlift.mcp.client.internal.InternalConnection.createInternalConnection;
import static io.airlift.mcp.client.internal.legacy.LegacyConnection.createLegacyConnection;
import static io.airlift.mcp.model.Protocol.PROTOCOL_MCP_2025_11_25;

public class LegacyOptionalConnection
        implements McpTasksConnection
{
    private final LegacyOptionalRetry retry;
    private final AtomicReference<McpTasksConnection> delegate;

    public static LegacyOptionalConnection createLegacyOptionalConnection(InternalClient internalClient, URI uri, SettingContainer settingContainer, boolean tasksEnabled)
    {
        return new LegacyOptionalConnection(internalClient, uri, settingContainer, tasksEnabled);
    }

    private LegacyOptionalConnection(InternalClient internalClient, URI uri, SettingContainer settingContainer, boolean tasksEnabled)
    {
        this.delegate = new AtomicReference<>(createInternalConnection(internalClient, uri, settingContainer, tasksEnabled));
        retry = new LegacyOptionalRetry(maybeError -> {
            boolean supportsLegacyProtocol = maybeError.map(error -> error.supported().contains(PROTOCOL_MCP_2025_11_25.value()))
                    .orElse(true);  // no UnsupportedProtocolVersionError present - assume it can support the legacy protocol
            if (supportsLegacyProtocol) {
                McpTasksConnection oldConnection = delegate.getAndSet(createLegacyConnection(internalClient, uri, settingContainer));
                oldConnection.close();
            }
        });
    }

    @Override
    public DiscoverResult serverDiscover()
    {
        return retry.withRetry(() -> delegate.get().serverDiscover());
    }

    @Override
    public DiscoverResult serverDiscover(Meta<?> meta)
    {
        return retry.withRetry(() -> delegate.get().serverDiscover(meta));
    }

    @Override
    public ListToolsResult listTools(Optional<String> cursor)
    {
        return retry.withRetry(() -> delegate.get().listTools(cursor));
    }

    @Override
    public ListPromptsResult listPrompts(Optional<String> cursor)
    {
        return retry.withRetry(() -> delegate.get().listPrompts(cursor));
    }

    @Override
    public ListResourcesResult listResources(Optional<String> cursor)
    {
        return retry.withRetry(() -> delegate.get().listResources(cursor));
    }

    @Override
    public ListResourceTemplatesResult listResourceTemplates(Optional<String> cursor)
    {
        return retry.withRetry(() -> delegate.get().listResourceTemplates(cursor));
    }

    @Override
    public CallToolResult callTool(CallToolRequest callToolRequest)
    {
        return retry.withRetry(() -> delegate.get().callTool(callToolRequest));
    }

    @Override
    public ToolResult callToolOrTask(CallToolRequest callToolRequest)
    {
        return retry.withRetry(() -> delegate.get().callToolOrTask(callToolRequest));
    }

    @Override
    public ToolResult getTask(GetTaskRequest request)
    {
        return retry.withRetry(() -> delegate.get().getTask(request));
    }

    @Override
    public void cancelTask(String taskId)
    {
        retry.withRetry(() -> {
            delegate.get().cancelTask(taskId);
            return null;
        });
    }

    @Override
    public void updateTask(UpdateTaskRequest request)
    {
        retry.withRetry(() -> {
            delegate.get().updateTask(request);
            return null;
        });
    }

    @Override
    public void sleepTask(Task task)
            throws InterruptedException
    {
        // no need for a retry here. InternalConnection merely sleeps
        delegate.get().sleepTask(task);
    }

    @Override
    public GetPromptResult getPrompt(GetPromptRequest getPromptRequest)
    {
        return retry.withRetry(() -> delegate.get().getPrompt(getPromptRequest));
    }

    @Override
    public ReadResourceResult readResource(ReadResourceRequest readResourceRequest)
    {
        return retry.withRetry(() -> delegate.get().readResource(readResourceRequest));
    }

    @Override
    public CompleteResult completeCompletion(CompleteRequest completeRequest)
    {
        return retry.withRetry(() -> delegate.get().completeCompletion(completeRequest));
    }

    @Override
    public AutoCloseable subscribe(SubscriptionNotifications subscriptionNotifications)
    {
        return retry.withRetry(() -> delegate.get().subscribe(subscriptionNotifications));
    }

    @Override
    public URI uri()
    {
        return delegate.get().uri();
    }

    @Override
    public <V> V setting(McpConnectionSetting<V> setting)
    {
        return delegate.get().setting(setting);
    }

    @Override
    public <V> McpTasksConnection withSetting(McpConnectionSetting<V> setting, V value)
    {
        return delegate.updateAndGet(localDelegate -> localDelegate.withSetting(setting, value));
    }

    @Override
    public void close()
    {
        delegate.get().close();
    }
}
