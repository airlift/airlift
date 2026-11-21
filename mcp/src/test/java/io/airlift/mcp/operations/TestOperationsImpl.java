package io.airlift.mcp.operations;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import io.airlift.json.JsonMapperProvider;
import io.airlift.mcp.McpConfig;
import io.airlift.mcp.McpEntities;
import io.airlift.mcp.McpException;
import io.airlift.mcp.McpMetadata;
import io.airlift.mcp.McpTasks;
import io.airlift.mcp.TestingIdentity;
import io.airlift.mcp.handler.ToolEntry;
import io.airlift.mcp.model.JsonRpcRequest;
import io.airlift.mcp.model.Task;
import io.airlift.mcp.model.Tool;
import io.airlift.mcp.reflection.IconHelper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.Optional;
import java.util.OptionalLong;

import static io.airlift.mcp.McpIdentity.Authenticated.authenticated;
import static io.airlift.mcp.model.Constants.HEADER_PROTOCOL_VERSION;
import static io.airlift.mcp.model.Constants.METADATA_CLIENT_CAPABILITIES;
import static io.airlift.mcp.model.Constants.METHOD_TOOLS_CALL;
import static io.airlift.mcp.model.JsonRpcErrorCode.MISSING_REQUIRED_CLIENT_CAPABILITY;
import static io.airlift.mcp.model.JsonRpcRequest.buildRequest;
import static io.airlift.mcp.model.Protocol.LATEST_PROTOCOL;
import static io.airlift.mcp.model.TaskStatus.WORKING;
import static io.airlift.mcp.model.TaskSupport.OPTIONAL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class TestOperationsImpl
{
    private static final JsonMapper JSON_MAPPER = new JsonMapperProvider().get();
    private static final Task TASK = new Task("task-id", WORKING, Optional.empty(), "created", "updated", OptionalLong.empty(), OptionalLong.empty(), Optional.empty(), Optional.empty(), Optional.empty());

    @Test
    public void testRefusedTaskIsCancelled()
    {
        McpTasks tasks = mock(McpTasks.class);
        McpEntities entities = mock(McpEntities.class);
        // a tool may create a task without its request context - for a client that cannot take one
        when(entities.toolEntry(any(), any())).thenReturn(Optional.of(new ToolEntry(mock(Tool.class), (_, _) -> TASK, OPTIONAL)));

        OperationsImpl operations = new OperationsImpl(
                _ -> McpMetadata.DEFAULT,
                JSON_MAPPER,
                new McpConfig(),
                mock(IconHelper.class),
                ImmutableSet.of(),
                entities,
                ValidationMode.LENIENT,
                Optional.of(tasks));

        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader(HEADER_PROTOCOL_VERSION)).thenReturn(LATEST_PROTOCOL.value());
        JsonRpcRequest<?> rpcRequest = buildRequest(1, METHOD_TOOLS_CALL, ImmutableMap.of(
                "name", "test",
                "_meta", ImmutableMap.of(METADATA_CLIENT_CAPABILITIES, ImmutableMap.of())));

        // the client is never told the task id, so the task must not be left working
        assertThatThrownBy(() -> operations.handleRpcRequest(request, mock(HttpServletResponse.class), authenticated(new TestingIdentity("Mr. Tester")), rpcRequest))
                .isInstanceOfSatisfying(McpException.class, exception -> assertThat(exception.errorDetail().code()).isEqualTo(MISSING_REQUIRED_CLIENT_CAPABILITY.code()));
        verify(tasks).cancelTask("task-id");
    }
}
