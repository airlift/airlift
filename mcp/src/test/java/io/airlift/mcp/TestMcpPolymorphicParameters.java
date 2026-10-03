package io.airlift.mcp;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Closer;
import com.google.inject.Module;
import io.airlift.http.server.testing.TestingHttpServer;
import io.airlift.mcp.TestJsonSchemaBuilder.Circle;
import io.airlift.mcp.TestJsonSchemaBuilder.Shape;
import io.airlift.mcp.TestJsonSchemaBuilder.Square;
import io.airlift.mcp.operations.legacy.sessions.StandardSessionController;
import io.airlift.mcp.storage.MemoryStorageController;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import static com.google.inject.Scopes.SINGLETON;
import static io.airlift.mcp.TestingClient.buildClient;
import static java.util.stream.Collectors.joining;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

@TestInstance(PER_CLASS)
public class TestMcpPolymorphicParameters
{
    private final Closer closer = Closer.create();
    private TestingClient client;

    @BeforeAll
    public void setup()
    {
        Function<McpModule.Builder, Module> applicator = builder -> builder
                .withIdentityMapper(TestingIdentity.class, binding -> binding.to(TestingIdentityMapper.class).in(SINGLETON))
                .withStorage(binding -> binding.to(MemoryStorageController.class).in(SINGLETON))
                .withLegacyBindings().withSessions(binding -> binding.to(StandardSessionController.class).in(SINGLETON))
                .withAllInClass(ShapeEndpoints.class)
                .build();
        TestingServer server = closer.register(new TestingServer(ImmutableMap.of(), Optional.empty(), applicator));
        String baseUri = server.injector().getInstance(TestingHttpServer.class).getBaseUrl().toString();
        client = buildClient(closer, baseUri, "tester");
    }

    @AfterAll
    public void teardown()
            throws IOException
    {
        closer.close();
    }

    @Test
    public void testRequiredPolymorphicParameter()
    {
        assertThat(callTool("describe-shape", ImmutableMap.of("shape", ImmutableMap.of("type", "CIRCLE", "radius", 2.5)))).isEqualTo("circle 2.5");
        assertThat(callTool("describe-shape", ImmutableMap.of("shape", ImmutableMap.of("type", "SQUARE", "side", 3.0)))).isEqualTo("square 3.0");
    }

    @Test
    public void testRequiredPolymorphicListParameter()
    {
        List<Map<String, Object>> shapes = ImmutableList.of(
                ImmutableMap.of("type", "CIRCLE", "radius", 2.5),
                ImmutableMap.of("type", "SQUARE", "side", 3.0));
        assertThat(callTool("describe-shapes", ImmutableMap.of("shapes", shapes))).isEqualTo("circle 2.5, square 3.0");
    }

    @Test
    public void testInvalidPolymorphicParameter()
    {
        CallToolResult result = client.mcpClient().callTool(CallToolRequest.builder("describe-shape").arguments(ImmutableMap.of("shape", ImmutableMap.of("type", "TRIANGLE"))).build());
        assertThat(result.isError()).isTrue();
        assertThat(result.content()).hasSize(1);
        assertThat(((TextContent) result.content().getFirst()).text()).startsWith("Invalid value for parameter shape: Could not resolve type id 'TRIANGLE'");
    }

    private String callTool(String name, Map<String, Object> arguments)
    {
        CallToolResult result = client.mcpClient().callTool(CallToolRequest.builder(name).arguments(arguments).build());
        assertThat(result.isError()).isFalse();
        assertThat(result.content()).hasSize(1);
        return ((TextContent) result.content().getFirst()).text();
    }

    @SuppressWarnings("unused")
    public static class ShapeEndpoints
    {
        @McpTool(name = "describe-shape", description = "Describe a shape")
        public String describeShape(Shape shape)
        {
            return describe(shape);
        }

        @McpTool(name = "describe-shapes", description = "Describe shapes")
        public String describeShapes(List<Shape> shapes)
        {
            return shapes.stream()
                    .map(ShapeEndpoints::describe)
                    .collect(joining(", "));
        }

        private static String describe(Shape shape)
        {
            return switch (shape) {
                case Circle circle -> "circle " + circle.radius();
                case Square square -> "square " + square.side();
            };
        }
    }
}
