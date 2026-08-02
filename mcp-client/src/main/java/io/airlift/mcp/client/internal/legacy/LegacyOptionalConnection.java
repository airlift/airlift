package io.airlift.mcp.client.internal.legacy;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Throwables;
import io.airlift.http.client.UnexpectedResponseException;
import io.airlift.mcp.McpException;
import io.airlift.mcp.client.McpConnection;
import io.airlift.mcp.client.McpConnectionSetting;
import io.airlift.mcp.client.McpMapper;
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
import io.airlift.mcp.model.UnsupportedProtocolVersionError;
import io.airlift.mcp.model.UpdateTaskRequest;

import java.net.URI;
import java.util.Optional;

import static io.airlift.http.client.HttpStatus.BAD_REQUEST;
import static io.airlift.mcp.client.internal.InternalConnection.createInternalConnection;
import static io.airlift.mcp.client.internal.legacy.LegacyConnection.createLegacyConnection;
import static io.airlift.mcp.client.internal.legacy.LegacyOptionalSharedState.State.CURRENT;
import static io.airlift.mcp.client.internal.legacy.LegacyOptionalSharedState.State.LATENT;
import static io.airlift.mcp.client.internal.legacy.LegacyOptionalSharedState.State.LEGACY;
import static io.airlift.mcp.model.JsonRpcErrorCode.INVALID_REQUEST;
import static io.airlift.mcp.model.JsonRpcErrorCode.UNSUPPORTED_PROTOCOL;
import static io.airlift.mcp.model.Protocol.PROTOCOL_MCP_2025_11_25;
import static java.util.Objects.requireNonNull;

public class LegacyOptionalConnection
        implements McpTasksConnection
{
    private final LegacyOptionalSharedState sharedState;
    private final LegacyOptionalThunk thunk;

    public static LegacyOptionalConnection createLegacyOptionalConnection(InternalClient client, URI uri, SettingContainer settingContainer, boolean tasksEnabled)
    {
        InternalConnection internalConnection = createInternalConnection(client, uri, settingContainer, tasksEnabled);
        LegacyOptionalSharedState sharedState = new LegacyOptionalSharedState(() -> createLegacyConnection(internalConnection.requestController()));
        return new LegacyOptionalConnection(sharedState, new LegacyOptionalThunk(internalConnection));
    }

    private LegacyOptionalConnection(LegacyOptionalSharedState sharedState, LegacyOptionalThunk thunk)
    {
        this.sharedState = requireNonNull(sharedState, "sharedState is null");
        this.thunk = requireNonNull(thunk, "thunk is null");
    }

    @Override
    public DiscoverResult serverDiscover()
    {
        return withRetry(McpConnection::serverDiscover);
    }

    @Override
    public ListToolsResult listTools(Optional<String> cursor)
    {
        return withRetry(connection -> connection.listTools(cursor));
    }

    @Override
    public ListPromptsResult listPrompts(Optional<String> cursor)
    {
        return withRetry(connection -> connection.listPrompts(cursor));
    }

    @Override
    public ListResourcesResult listResources(Optional<String> cursor)
    {
        return withRetry(connection -> connection.listResources(cursor));
    }

    @Override
    public ListResourceTemplatesResult listResourceTemplates(Optional<String> cursor)
    {
        return withRetry(connection -> connection.listResourceTemplates(cursor));
    }

    @Override
    public CallToolResult callTool(CallToolRequest callToolRequest)
    {
        return withRetry(connection -> connection.callTool(callToolRequest));
    }

    @Override
    public ToolResult callToolOrTask(CallToolRequest callToolRequest)
    {
        return withRetry(connection -> connection.callToolOrTask(callToolRequest));
    }

    @Override
    public ToolResult getTask(GetTaskRequest request)
    {
        return withRetry(connection -> connection.getTask(request));
    }

    @Override
    public void cancelTask(String taskId)
    {
        withRetry(connection -> {
            connection.cancelTask(taskId);
            return null;
        });
    }

    @Override
    public void updateTask(UpdateTaskRequest request)
    {
        withRetry(connection -> {
            connection.updateTask(request);
            return null;
        });
    }

    @Override
    public void sleepTask(Task task)
            throws InterruptedException
    {
        // no need for a retry here. InternalConnection merely sleeps
        thunk.internalConnection().sleepTask(task);
    }

    @Override
    public GetPromptResult getPrompt(GetPromptRequest getPromptRequest)
    {
        return withRetry(connection -> connection.getPrompt(getPromptRequest));
    }

    @Override
    public ReadResourceResult readResource(ReadResourceRequest readResourceRequest)
    {
        return withRetry(connection -> connection.readResource(readResourceRequest));
    }

    @Override
    public CompleteResult completeCompletion(CompleteRequest completeRequest)
    {
        return withRetry(connection -> connection.completeCompletion(completeRequest));
    }

    @Override
    public AutoCloseable subscribe(SubscriptionNotifications subscriptionNotifications)
    {
        return withRetry(connection -> connection.subscribe(subscriptionNotifications));
    }

    @Override
    public URI uri()
    {
        return thunk.internalConnection().uri();
    }

    @Override
    public <V> V setting(McpConnectionSetting<V> setting)
    {
        return thunk.get(sharedState).setting(setting);
    }

    @Override
    public <V> McpTasksConnection withSetting(McpConnectionSetting<V> setting, V value)
    {
        LegacyOptionalThunk newThunk = thunk.withSetting(setting, value);
        return new LegacyOptionalConnection(sharedState, newThunk);
    }

    @SuppressWarnings("EmptyTryBlock")
    @Override
    public void close()
    {
        try (sharedState; thunk) {
            // NOP
        }
    }

    private interface Handler<R>
    {
        R call(McpTasksConnection connection);
    }

    private <R> R withRetry(Handler<R> proc)
    {
        if (sharedState.state() == LATENT) {
            sharedState.transitionLock().lock();
            try {
                // essentially a double-checked lock to avoid unnecessary locking in the common case
                if (sharedState.state() == LATENT) {
                    try {
                        R result = proc.call(thunk.get(sharedState));
                        thunk.transition(sharedState, CURRENT);
                        return result;
                    }
                    catch (McpException mcpException) {
                        if (indicatesLegacyProtocol(mcpException)) {
                            thunk.transition(sharedState, LEGACY);
                            // will retry with the legacy connection below
                        }
                        else {
                            // the current protocol is fine - this failure was about something else
                            thunk.transition(sharedState, CURRENT);
                            throw mcpException;
                        }
                    }
                }
            }
            finally {
                sharedState.transitionLock().unlock();
            }
        }

        return proc.call(thunk.get(sharedState));
    }

    @VisibleForTesting
    static boolean indicatesLegacyProtocol(McpException mcpException)
    {
        if (mcpException.errorDetail().code() == UNSUPPORTED_PROTOCOL.code()) {
            return parseProtocolError(mcpException)
                    .map(error -> error.supported().contains(PROTOCOL_MCP_2025_11_25.value()))
                    .orElse(true);  // no UnsupportedProtocolVersionError present - assume it can support the legacy protocol
        }

        if ((Throwables.getRootCause(mcpException) instanceof UnexpectedResponseException responseException) && (responseException.getStatusCode() == BAD_REQUEST.code())) {
            return true;
        }

        return mcpException.errorDetail().code() == INVALID_REQUEST.code();
    }

    private static Optional<UnsupportedProtocolVersionError> parseProtocolError(McpException mcpException)
    {
        return mcpException.errorDetail().data()
                .flatMap(data -> {
                    try {
                        return Optional.of(McpMapper.jsonMapper().convertValue(data, UnsupportedProtocolVersionError.class));
                    }
                    catch (IllegalArgumentException _) {
                        // ignore
                    }
                    return Optional.empty();
                });
    }
}
