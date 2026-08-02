package io.airlift.mcp.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.airlift.mcp.McpClientException;
import io.airlift.mcp.model.CallToolResult;
import io.airlift.mcp.model.Content.TextContent;
import io.airlift.mcp.model.ListToolsResult;
import io.airlift.mcp.model.StructuredContent;
import io.airlift.mcp.model.Tool;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static io.airlift.mcp.client.McpMapper.jsonMapper;
import static io.airlift.mcp.client.McpMapper.optionalContentString;
import static io.airlift.mcp.client.McpMapper.optionalStructuredContent;
import static io.airlift.mcp.client.McpMapper.requireContentString;
import static io.airlift.mcp.client.McpMapper.requireStructuredContent;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class TestMcpMapper
{
    public record Sum(int total) {}

    @Test
    public void testStructuredContentIsRead()
    {
        CallToolResult result = structuredResult(ImmutableMap.of("total", 3));

        assertThat(requireStructuredContent(result, Sum.class)).isEqualTo(new Sum(3));
        assertThat(optionalStructuredContent(result, Sum.class)).contains(new Sum(3));
    }

    @Test
    public void testAbsentStructuredContent()
    {
        CallToolResult result = new CallToolResult(ImmutableList.of(), Optional.empty(), false);

        assertThat(optionalStructuredContent(result, Sum.class)).isEmpty();
        assertThatThrownBy(() -> requireStructuredContent(result, Sum.class))
                .isInstanceOf(McpClientException.class)
                .hasMessageContaining("Expected structured content");
    }

    @Test
    public void testStructuredContentOfAnotherShape()
    {
        // the result arrived but cannot be read as asked, which either form reports the same way - keeping why as the cause
        CallToolResult result = structuredResult(ImmutableMap.of("total", "not a number"));

        assertThatThrownBy(() -> optionalStructuredContent(result, Sum.class))
                .isInstanceOf(McpClientException.class)
                .hasMessageContaining(Sum.class.getName())
                .hasRootCauseInstanceOf(JsonProcessingException.class);
        assertThatThrownBy(() -> requireStructuredContent(result, Sum.class))
                .isInstanceOf(McpClientException.class)
                .hasMessageContaining(Sum.class.getName());
    }

    @Test
    public void testToolErrorIsNotReadAsAResult()
    {
        // a tool reports its failure in a successful response - its error text is not the tool's output
        CallToolResult result = new CallToolResult(ImmutableList.of(new TextContent("disk full")), Optional.of(new StructuredContent<>(ImmutableMap.of("total", 3))), true);

        assertThatThrownBy(() -> requireContentString(result))
                .isInstanceOfSatisfying(McpClientException.class, exception -> {
                    assertThat(exception).hasMessage("disk full");
                    // the whole result stays available
                    assertThat(exception.unwrap().errorDetail().data()).contains(result);
                });
        assertThatThrownBy(() -> optionalContentString(result)).isInstanceOf(McpClientException.class);
        assertThatThrownBy(() -> requireStructuredContent(result, Sum.class)).isInstanceOf(McpClientException.class);
        assertThatThrownBy(() -> optionalStructuredContent(result, Sum.class)).isInstanceOf(McpClientException.class);
    }

    @Test
    public void testToolsOfAnyNameAreRead()
    {
        // the naming rules are recommendations, so a server may name its tools otherwise - one that does must not
        // keep its other tools from being listed
        Map<String, Object> listing = ImmutableMap.of("tools", ImmutableList.of(
                ImmutableMap.of("name", "add", "inputSchema", ImmutableMap.of("type", "object")),
                ImmutableMap.of("name", "github/search", "inputSchema", ImmutableMap.of("type", "object"))));

        ListToolsResult result = jsonMapper().convertValue(listing, ListToolsResult.class);

        assertThat(result.tools()).extracting(Tool::name).containsExactly("add", "github/search");
    }

    private static CallToolResult structuredResult(Object structuredContent)
    {
        return new CallToolResult(ImmutableList.of(), Optional.of(new StructuredContent<>(structuredContent)), false);
    }
}
