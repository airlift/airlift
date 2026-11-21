package io.airlift.mcp;

import com.google.common.collect.ImmutableMap;
import com.google.inject.CreationException;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.TypeLiteral;
import io.airlift.mcp.handler.ToolEntry;
import io.airlift.mcp.model.CallToolResult;
import io.airlift.mcp.model.Content.TextContent;
import io.airlift.mcp.model.TaskSupport;
import io.airlift.mcp.model.Tool;
import io.airlift.mcp.tasks.memory.MemoryTaskEngine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import static com.google.inject.Scopes.SINGLETON;
import static io.airlift.mcp.McpIdentity.Authenticated.authenticated;
import static io.airlift.mcp.model.TaskSupport.NONE;
import static io.airlift.mcp.model.TaskSupport.OPTIONAL;
import static io.airlift.mcp.model.TaskSupport.REQUIRED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A tool declared with a {@link TaskSupport} other than {@link TaskSupport#NONE} cannot run without a
 * task engine, so a server that has one but no engine is refused at startup.
 */
public class TestTaskEngineRequired
{
    @ParameterizedTest
    @EnumSource(value = TaskSupport.class, names = {"OPTIONAL", "REQUIRED"})
    public void testTaskToolWithoutEngineFailsAtStartup(TaskSupport taskSupport)
    {
        Class<?> toolClass = (taskSupport == REQUIRED) ? RequiredTaskTool.class : OptionalTaskTool.class;

        assertThatThrownBy(() -> server(false, toolClass).close())
                .isInstanceOf(CreationException.class)
                .hasMessageContaining("Tool task_tool is declared with taskSupport %s, which requires a task engine: bind McpTasks with McpModule.Builder.withTasks()", taskSupport);
    }

    @ParameterizedTest
    @EnumSource(value = TaskSupport.class, names = {"OPTIONAL", "REQUIRED"})
    public void testTaskToolWithEngineStarts(TaskSupport taskSupport)
    {
        Class<?> toolClass = (taskSupport == REQUIRED) ? RequiredTaskTool.class : OptionalTaskTool.class;

        try (TestingServer testingServer = server(true, toolClass)) {
            assertThat(toolEntries(testingServer))
                    .extracting(ToolEntry::taskSupport)
                    .containsExactly(taskSupport);
        }
    }

    @Test
    public void testPlainToolWithoutEngineStarts()
    {
        try (TestingServer testingServer = server(false, PlainTool.class)) {
            assertThat(toolEntries(testingServer))
                    .extracting(ToolEntry::taskSupport)
                    .containsExactly(NONE);
        }
    }

    @Test
    public void testAddingTaskToolWithoutEngineIsRejected()
    {
        try (TestingServer testingServer = server(false, PlainTool.class)) {
            McpEntities entities = testingServer.injector().getInstance(McpEntities.class);
            Tool tool = toolEntries(testingServer).iterator().next().tool();
            Tool taskTool = new Tool("added_task_tool", tool.description(), tool.title(), tool.inputSchema(), tool.outputSchema(), tool.annotations(), tool.icons(), tool.meta());

            assertThatThrownBy(() -> entities.addTool(new ToolEntry(taskTool, (_, _) -> new CallToolResult(new TextContent("never")), OPTIONAL)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Tool added_task_tool is declared with taskSupport OPTIONAL, which requires a task engine: bind McpTasks with McpModule.Builder.withTasks()");

            // the same tool without a task declaration is accepted
            entities.addTool(new ToolEntry(taskTool, (_, _) -> new CallToolResult(new TextContent("added")), NONE));
        }
    }

    private static TestingServer server(boolean withEngine, Class<?> toolClass)
    {
        Function<McpModule.Builder, Module> mcpModuleBuilder = builder -> {
            builder.withIdentityMapper(TestingIdentity.class, binding -> binding.toInstance(_ -> authenticated(new TestingIdentity("Mr. Tester"))))
                    .withAllInClass(toolClass);
            if (withEngine) {
                builder.withTasks(binding -> binding.to(MemoryTaskEngine.class).in(SINGLETON));
            }
            return builder.build();
        };

        return new TestingServer(ImmutableMap.of(), Optional.empty(), mcpModuleBuilder);
    }

    private static Set<ToolEntry> toolEntries(TestingServer testingServer)
    {
        return testingServer.injector().getInstance(Key.get(new TypeLiteral<Set<ToolEntry>>() {}));
    }

    public static class RequiredTaskTool
    {
        @McpTool(name = "task_tool", description = "test", taskSupport = REQUIRED)
        public CallToolResult taskTool()
        {
            return new CallToolResult(new TextContent("unused"));
        }
    }

    public static class OptionalTaskTool
    {
        @McpTool(name = "task_tool", description = "test", taskSupport = OPTIONAL)
        public CallToolResult taskTool()
        {
            return new CallToolResult(new TextContent("unused"));
        }
    }

    public static class PlainTool
    {
        @McpTool(name = "plain_tool", description = "test")
        public CallToolResult plainTool()
        {
            return new CallToolResult(new TextContent("plain"));
        }
    }
}
