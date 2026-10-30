package io.airlift.mcp.handler;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.google.common.collect.ImmutableList;
import io.airlift.mcp.model.CallToolResult;
import io.airlift.mcp.model.Tool;
import io.airlift.mcp.model.Tool.ToolAnnotations;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class TestToolEntry
{
    private static final ToolHandler HANDLER = (_, _) -> new CallToolResult(ImmutableList.of());

    @Test
    public void testRegisteredToolNameIsChecked()
    {
        assertThat(new ToolEntry(tool("admin.tools-list_v2"), HANDLER).tool().name()).isEqualTo("admin.tools-list_v2");

        assertThatThrownBy(() -> new ToolEntry(tool("github/search"), HANDLER))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Tool name may only contain ASCII letters, digits, '_', '-' and '.': github/search");
        assertThatThrownBy(() -> new ToolEntry(tool("x".repeat(129)), HANDLER))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageStartingWith("Tool name must be between 1 and 128 characters");
    }

    @Test
    public void testToolOfAnotherServerMayHaveAnyName()
    {
        // the naming rules are recommendations - a tool read from another server is not held to them
        assertThat(tool("github/search").name()).isEqualTo("github/search");
        assertThat(tool("my tool").name()).isEqualTo("my tool");
    }

    private static Tool tool(String name)
    {
        ToolAnnotations annotations = new ToolAnnotations(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
        return new Tool(name, Optional.empty(), Optional.empty(), JsonNodeFactory.instance.objectNode(), Optional.empty(), annotations);
    }
}
