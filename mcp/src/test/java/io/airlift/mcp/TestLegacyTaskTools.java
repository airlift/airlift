package io.airlift.mcp;

import com.google.common.collect.ImmutableMap;
import com.google.common.io.Closer;
import com.google.inject.Inject;
import io.airlift.http.server.testing.TestingHttpServer;
import io.airlift.mcp.model.CallToolRequest;
import io.airlift.mcp.model.CallToolResult;
import io.airlift.mcp.model.Content.TextContent;
import io.airlift.mcp.model.ToolResult;
import io.airlift.mcp.operations.legacy.sessions.StandardSessionController;
import io.airlift.mcp.operations.legacy.storage.MemoryStorageController;
import io.airlift.mcp.tasks.memory.MemoryTaskEngine;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.IOException;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.google.inject.Scopes.SINGLETON;
import static io.airlift.mcp.McpIdentity.Authenticated.authenticated;
import static io.airlift.mcp.TestingClient.buildClient;
import static io.airlift.mcp.model.Constants.EXTENSION_TASKS;
import static io.airlift.mcp.model.TaskSupport.OPTIONAL;
import static io.airlift.mcp.model.TaskSupport.REQUIRED;
import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

/**
 * The legacy protocols cannot carry a task, and they do not carry {@code taskSupport} either, so a
 * client of theirs can ask for a tool it is not able to call.
 */
@TestInstance(PER_CLASS)
public class TestLegacyTaskTools
{
    private static final AtomicBoolean REQUIRED_TOOL_CALLED = new AtomicBoolean();

    private final Closer closer = Closer.create();
    private final TestingClient client;

    public TestLegacyTaskTools()
    {
        TestingServer testingServer = new TestingServer(ImmutableMap.of(), Optional.empty(), builder -> builder
                .withIdentityMapper(TestingIdentity.class, binding -> binding.toInstance(_ -> authenticated(new TestingIdentity("Mr. Tester"))))
                .withTasks(binding -> binding.to(MemoryTaskEngine.class).in(SINGLETON))
                .withLegacyBindings(legacyBuilder -> legacyBuilder
                        .withStorage(binding -> binding.to(MemoryStorageController.class).in(SINGLETON))
                        .withSessions(binding -> binding.to(StandardSessionController.class).in(SINGLETON)))
                .withAllInClass(TaskTools.class)
                .build());
        closer.register(testingServer);

        String baseUri = testingServer.injector().getInstance(TestingHttpServer.class).getBaseUrl().toString();
        client = buildClient(closer, baseUri, "client");
    }

    @AfterAll
    public void shutdown()
            throws IOException
    {
        closer.close();
    }

    public static class TaskTools
    {
        private final McpTasks tasks;

        @Inject
        public TaskTools(McpTasks tasks)
        {
            this.tasks = requireNonNull(tasks, "tasks is null");
        }

        @McpTool(name = "always_a_task", description = "test", taskSupport = REQUIRED)
        public ToolResult alwaysATask(McpRequestContext requestContext, CallToolRequest callToolRequest)
        {
            REQUIRED_TOOL_CALLED.set(true);
            return requestContext.createTask(callToolRequest);
        }

        @McpTool(name = "maybe_a_task", description = "test", taskSupport = OPTIONAL)
        public CallToolResult maybeATask()
        {
            return new CallToolResult(new TextContent("ran synchronously"));
        }

        // a tool is free to create a task itself, which the legacy protocols have no way of returning
        @McpTool(name = "a_task_anyway", description = "test", taskSupport = OPTIONAL)
        public ToolResult aTaskAnyway(McpRequestContext requestContext, CallToolRequest callToolRequest)
        {
            return tasks.createTask(requestContext, callToolRequest, Duration.ofMinutes(1), Duration.ofSeconds(5));
        }
    }

    @Test
    public void testRequiredTaskToolIsRejected()
    {
        McpSchema.CallToolResult result = client.mcpClient().callTool(McpSchema.CallToolRequest.builder("always_a_task").arguments(ImmutableMap.of()).build());

        // the tool is never entered - the client is told what it is missing instead
        assertThat(result.isError()).isTrue();
        assertThat(((McpSchema.TextContent) result.content().getFirst()).text()).contains(EXTENSION_TASKS);
        assertThat(REQUIRED_TOOL_CALLED).isFalse();
    }

    @Test
    public void testATaskFromALegacyClientIsAnInBandError()
    {
        McpSchema.CallToolResult result = client.mcpClient().callTool(McpSchema.CallToolRequest.builder("a_task_anyway").arguments(ImmutableMap.of()).build());

        // out of band this would invalidate the client's session
        assertThat(result.isError()).isTrue();
        assertThat(((McpSchema.TextContent) result.content().getFirst()).text()).contains("a_task_anyway");
    }

    @Test
    public void testOptionalTaskToolRunsSynchronously()
    {
        McpSchema.CallToolResult result = client.mcpClient().callTool(McpSchema.CallToolRequest.builder("maybe_a_task").arguments(ImmutableMap.of()).build());

        assertThat(result.content()).hasSize(1);
        assertThat(((McpSchema.TextContent) result.content().getFirst()).text()).isEqualTo("ran synchronously");
    }
}
