package io.airlift.mcp.operations;

import com.fasterxml.jackson.databind.json.JsonMapper;
import io.airlift.json.JsonMapperProvider;
import io.airlift.mcp.McpException;
import io.airlift.mcp.McpTasks;
import io.airlift.mcp.TestingIdentity;
import io.airlift.mcp.messages.MessageWriter;
import io.airlift.mcp.model.CallToolRequest;
import io.airlift.mcp.model.CallToolResult;
import io.airlift.mcp.model.Content.TextContent;
import io.airlift.mcp.model.InitializeRequest.ClientCapabilities;
import io.airlift.mcp.model.Task;
import io.airlift.mcp.model.TaskSupport;
import io.airlift.mcp.tasks.TaskExecutor;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Duration;
import java.util.Optional;
import java.util.OptionalLong;

import static io.airlift.mcp.McpIdentity.Authenticated.authenticated;
import static io.airlift.mcp.model.Constants.EXTENSION_TASKS;
import static io.airlift.mcp.model.JsonRpcErrorCode.MISSING_REQUIRED_CLIENT_CAPABILITY;
import static io.airlift.mcp.model.Protocol.LATEST_PROTOCOL;
import static io.airlift.mcp.model.TaskStatus.WORKING;
import static io.airlift.mcp.model.TaskSupport.NONE;
import static io.airlift.mcp.model.TaskSupport.OPTIONAL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

public class TestRequestContextImpl
{
    private static final JsonMapper JSON_MAPPER = new JsonMapperProvider().get();
    private static final Duration TASK_TTL = Duration.ofMinutes(1);
    private static final Duration TASK_POLL_INTERVAL = Duration.ofSeconds(1);
    private static final CallToolRequest CALL_TOOL_REQUEST = new CallToolRequest("test");
    private static final TaskExecutor EXECUTOR = (_, _, _) -> new CallToolResult(new TextContent("done"));

    @Test
    public void testCreateTaskWithoutTasksExtension()
    {
        McpTasks tasks = mock(McpTasks.class);
        RequestContextImpl requestContext = requestContext(tasks, ClientCapabilities.EMPTY, OPTIONAL);

        assertThatThrownBy(() -> requestContext.createTask(CALL_TOOL_REQUEST))
                .isInstanceOfSatisfying(McpException.class, TestRequestContextImpl::assertMissingTasksExtension);
        verifyNoInteractions(tasks);
    }

    @Test
    public void testCreateAndExecuteTaskWithoutTasksExtension()
    {
        McpTasks tasks = mock(McpTasks.class);
        RequestContextImpl requestContext = requestContext(tasks, ClientCapabilities.EMPTY, OPTIONAL);

        // nothing may be stored or run for a client that can never learn the task id
        assertThatThrownBy(() -> requestContext.createAndExecuteTask(CALL_TOOL_REQUEST, EXECUTOR))
                .isInstanceOfSatisfying(McpException.class, TestRequestContextImpl::assertMissingTasksExtension);
        verifyNoInteractions(tasks);
    }

    @ParameterizedTest
    @EnumSource(value = TaskSupport.class, names = {"OPTIONAL", "REQUIRED"})
    public void testCreateAndExecuteTaskWithTasksExtension(TaskSupport taskSupport)
    {
        McpTasks tasks = mock(McpTasks.class);
        Task task = new Task("task-id", WORKING, Optional.empty(), "created", "updated", OptionalLong.empty(), OptionalLong.empty(), Optional.empty(), Optional.empty(), Optional.empty());
        RequestContextImpl requestContext = requestContext(tasks, ClientCapabilities.requiredExtension(EXTENSION_TASKS), taskSupport);
        when(tasks.createTask(requestContext, CALL_TOOL_REQUEST, TASK_TTL, TASK_POLL_INTERVAL)).thenReturn(task);

        assertThat(requestContext.createAndExecuteTask(CALL_TOOL_REQUEST, EXECUTOR)).isSameAs(task);
        verify(tasks).executeTask("task-id", EXECUTOR);
    }

    @Test
    public void testToolWithoutTaskSupportCannotCreateATask()
    {
        McpTasks tasks = mock(McpTasks.class);
        // the client could take a task - the tool did not declare that it makes them
        RequestContextImpl requestContext = requestContext(tasks, ClientCapabilities.requiredExtension(EXTENSION_TASKS), NONE);

        assertThatThrownBy(() -> requestContext.createTask(CALL_TOOL_REQUEST))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Tool test is declared with taskSupport NONE and cannot create a task");
        assertThatThrownBy(() -> requestContext.createAndExecuteTask(CALL_TOOL_REQUEST, EXECUTOR))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(tasks);
    }

    @Test
    public void testUndeclaredToolFailsBeforeTheClientIsChecked()
    {
        McpTasks tasks = mock(McpTasks.class);
        RequestContextImpl requestContext = requestContext(tasks, ClientCapabilities.EMPTY, NONE);

        // the mistake is the tool's, so it must show whatever the client supports
        assertThatThrownBy(() -> requestContext.createTask(CALL_TOOL_REQUEST))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(tasks);
    }

    @Test
    public void testOnlyAToolCallCanCreateATask()
    {
        McpTasks tasks = mock(McpTasks.class);
        // the context every request starts with - a prompt, resource or completion handler sees this one
        RequestContextImpl requestContext = newRequestContext(tasks, ClientCapabilities.requiredExtension(EXTENSION_TASKS));

        assertThatThrownBy(() -> requestContext.createTask(CALL_TOOL_REQUEST))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(tasks);
    }

    private static RequestContextImpl requestContext(McpTasks tasks, ClientCapabilities clientCapabilities, TaskSupport taskSupport)
    {
        return newRequestContext(tasks, clientCapabilities).withTaskSupport(taskSupport);
    }

    private static RequestContextImpl newRequestContext(McpTasks tasks, ClientCapabilities clientCapabilities)
    {
        RequestMetadata requestMetadata = new RequestMetadata(LATEST_PROTOCOL, Optional.empty(), clientCapabilities, Optional.empty(), Optional.empty(), Optional.empty());

        return new RequestContextImpl(
                mock(HttpServletRequest.class),
                requestMetadata,
                JSON_MAPPER,
                mock(MessageWriter.class),
                authenticated(new TestingIdentity("Mr. Tester")),
                Optional.of(tasks),
                TASK_TTL,
                TASK_POLL_INTERVAL);
    }

    private static void assertMissingTasksExtension(McpException exception)
    {
        assertThat(exception.errorDetail().code()).isEqualTo(MISSING_REQUIRED_CLIENT_CAPABILITY.code());
    }
}
