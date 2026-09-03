package io.airlift.mcp.client.internal.legacy;

import io.airlift.mcp.client.McpConnectionSetting;
import io.airlift.mcp.client.McpTasksConnection;
import io.airlift.mcp.client.internal.InternalClient;
import io.airlift.mcp.client.internal.InternalConnection;
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
import io.airlift.mcp.model.ReadResourceRequest;
import io.airlift.mcp.model.ReadResourceResult;
import io.airlift.mcp.model.SubscriptionNotifications;
import io.airlift.mcp.model.Task;
import io.airlift.mcp.model.ToolResult;
import io.airlift.mcp.model.UpdateTaskRequest;

import java.net.URI;
import java.util.Optional;

import static io.airlift.mcp.client.internal.InternalConnection.createInternalConnection;
import static io.airlift.mcp.client.internal.legacy.LegacyConnection.createLegacyConnection;
import static io.airlift.mcp.client.internal.legacy.LegacyOptionalSharedState.State.LATENT;
import static java.util.Objects.requireNonNull;

public class LegacyOptionalConnection
        implements McpTasksConnection
{
    private final LegacyOptionalSharedState sharedState;
    private final InternalConnection internalConnection;

    public static LegacyOptionalConnection createLegacyOptionalConnection(InternalClient client, URI uri, SettingContainer settingContainer, boolean tasksEnabled)
    {
        InternalConnection internalConnection = createInternalConnection(client, uri, settingContainer, tasksEnabled);
        LegacyOptionalSharedState sharedState = new LegacyOptionalSharedState(() -> createLegacyConnection(internalConnection.requestController()));
        return new LegacyOptionalConnection(sharedState, internalConnection);
    }

    private LegacyOptionalConnection(LegacyOptionalSharedState sharedState, InternalConnection internalConnection)
    {
        this.sharedState = requireNonNull(sharedState, "sharedState is null");
        this.internalConnection = requireNonNull(internalConnection, "internalConnection is null");
    }

    @Override
    public DiscoverResult serverDiscover()
    {
        return sharedState.withConnectionResolution(() -> connection().serverDiscover());
    }

    @Override
    public ListToolsResult listTools(Optional<String> cursor)
    {
        return sharedState.withConnectionResolution(() -> connection().listTools(cursor));
    }

    @Override
    public ListPromptsResult listPrompts(Optional<String> cursor)
    {
        return sharedState.withConnectionResolution(() -> connection().listPrompts(cursor));
    }

    @Override
    public ListResourcesResult listResources(Optional<String> cursor)
    {
        return sharedState.withConnectionResolution(() -> connection().listResources(cursor));
    }

    @Override
    public ListResourceTemplatesResult listResourceTemplates(Optional<String> cursor)
    {
        return sharedState.withConnectionResolution(() -> connection().listResourceTemplates(cursor));
    }

    @Override
    public CallToolResult callTool(CallToolRequest callToolRequest)
    {
        return sharedState.withConnectionResolution(() -> connection().callTool(callToolRequest));
    }

    @Override
    public ToolResult callToolOrTask(CallToolRequest callToolRequest)
    {
        return sharedState.withConnectionResolution(() -> connection().callToolOrTask(callToolRequest));
    }

    @Override
    public ToolResult getTask(GetTaskRequest request)
    {
        return sharedState.withConnectionResolution(() -> connection().getTask(request));
    }

    @Override
    public void cancelTask(String taskId)
    {
        sharedState.withConnectionResolution(() -> {
            connection().cancelTask(taskId);
            return null;
        });
    }

    @Override
    public void updateTask(UpdateTaskRequest request)
    {
        sharedState.withConnectionResolution(() -> {
            connection().updateTask(request);
            return null;
        });
    }

    @Override
    public void sleepTask(Task task)
            throws InterruptedException
    {
        // no need for a retry here. InternalConnection merely sleeps
        internalConnection.sleepTask(task);
    }

    @Override
    public GetPromptResult getPrompt(GetPromptRequest getPromptRequest)
    {
        return sharedState.withConnectionResolution(() -> connection().getPrompt(getPromptRequest));
    }

    @Override
    public ReadResourceResult readResource(ReadResourceRequest readResourceRequest)
    {
        return sharedState.withConnectionResolution(() -> connection().readResource(readResourceRequest));
    }

    @Override
    public CompleteResult completeCompletion(CompleteRequest completeRequest)
    {
        return sharedState.withConnectionResolution(() -> connection().completeCompletion(completeRequest));
    }

    @Override
    public AutoCloseable subscribe(SubscriptionNotifications subscriptionNotifications)
    {
        if (sharedState.state() == LATENT) {
            // connection hasn't been resolved yet, and subscriptions are background tasks
            // use serverDiscover() to force connection resolution
            serverDiscover();
        }

        // withConnectionResolution() is not needed here as above code is guaranteed to resolve the connection
        return connection().subscribe(subscriptionNotifications);
    }

    @Override
    public URI uri()
    {
        return internalConnection.uri();
    }

    @Override
    public <V> V setting(McpConnectionSetting<V> setting)
    {
        // internalConnection is source of truth for settings
        return internalConnection.setting(setting);
    }

    @Override
    public <V> McpTasksConnection withSetting(McpConnectionSetting<V> setting, V value)
    {
        // internalConnection is source of truth for settings
        InternalConnection updatedInternalConnection = internalConnection.withSetting(setting, value);
        return new LegacyOptionalConnection(sharedState, updatedInternalConnection);
    }

    @SuppressWarnings("EmptyTryBlock")
    @Override
    public void close()
    {
        try (sharedState; internalConnection) {
            // NOP
        }
    }

    private McpTasksConnection connection()
    {
        return switch (sharedState.state()) {
            case LEGACY -> sharedState.legacyConnection().withMergedSettingContainer(internalConnection.settingContainer());
            case LATENT, CURRENT -> internalConnection;
        };
    }
}
