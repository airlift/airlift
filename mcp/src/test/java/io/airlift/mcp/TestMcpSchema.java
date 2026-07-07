package io.airlift.mcp;

import com.google.common.collect.ImmutableMap;
import com.google.common.io.Closer;
import com.google.inject.Module;
import io.airlift.http.server.testing.TestingHttpServer;
import io.airlift.mcp.operations.legacy.sessions.StandardSessionController;
import io.airlift.mcp.storage.MemoryStorageController;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static com.google.inject.Scopes.SINGLETON;
import static io.airlift.mcp.TestingClient.buildClient;
import static java.util.function.Function.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests the {@link McpSchema} override feature: {@code @McpTool(inputSchema = ...)} and
 * {@code @McpTool(outputSchema = ...)} let a tool replace the reflection-derived input/output
 * schema with one built from a raw JSON string, a class, or a fully-qualified class name.
 */
@SuppressWarnings("SameParameterValue")
public class TestMcpSchema
{
    // A raw schema whose distinctive "custom" property proves it was used verbatim rather than
    // being derived from the tool method's parameters/return type.
    private static final String RAW_SCHEMA = "{\"type\":\"object\",\"properties\":{\"custom\":{\"type\":\"string\"}},\"required\":[\"custom\"]}";

    private static final String CUSTOM_INPUT_CLASS_NAME = "io.airlift.mcp.TestMcpSchema$CustomInput";

    public record CustomInput(String alpha, int beta) {}

    public record CustomOutput(boolean ok) {}

    private final Closer closer = Closer.create();

    private Map<String, Tool> goodTools;

    @AfterEach
    public void teardown()
            throws IOException
    {
        closer.close();
    }

    @Test
    public void testDefaultSchemasWhenNoOverride()
    {
        Tool tool = tool("no-override");

        // input schema is derived from the method parameters
        assertThat(tool.inputSchema().get("type")).isEqualTo("object");
        assertThat(propertyNames(tool.inputSchema())).containsExactlyInAnyOrder("a", "b");

        // a plain (non-record) return type produces no output schema
        assertThat(tool.outputSchema()).isNull();
    }

    @Test
    public void testRawInputSchemaOverride()
    {
        Tool tool = tool("raw-input");

        Map<String, Object> inputSchema = tool.inputSchema();
        assertThat(inputSchema.get("type")).isEqualTo("object");
        // the raw schema is used verbatim - the method's "ignored" parameter does not appear
        assertThat(propertyNames(inputSchema)).containsExactly("custom");
        assertThat(property(inputSchema, "custom").get("type")).isEqualTo("string");
    }

    @Test
    public void testClassInputSchemaOverride()
    {
        Tool tool = tool("class-input");

        assertThat(tool.inputSchema().get("type")).isEqualTo("object");
        assertThat(propertyNames(tool.inputSchema())).containsExactlyInAnyOrder("alpha", "beta");
    }

    @Test
    public void testClassNameInputSchemaOverride()
    {
        Tool byName = tool("classname-input");
        Tool byClass = tool("class-input");

        // resolving the schema by class name yields the same schema as resolving it by class
        // (ignoring the description, which is derived from the differing tool names)
        assertThat(schemaWithoutDescription(byName.inputSchema())).isEqualTo(schemaWithoutDescription(byClass.inputSchema()));
        assertThat(propertyNames(byName.inputSchema())).containsExactlyInAnyOrder("alpha", "beta");
    }

    @Test
    public void testRawOutputSchemaOverride()
    {
        Tool tool = tool("raw-output");

        assertThat(tool.outputSchema()).isNotNull();
        assertThat(tool.outputSchema().get("type")).isEqualTo("object");
        assertThat(propertyNames(tool.outputSchema())).containsExactly("custom");
    }

    @Test
    public void testClassOutputSchemaOverride()
    {
        Tool tool = tool("class-output");

        assertThat(tool.outputSchema()).isNotNull();
        assertThat(propertyNames(tool.outputSchema())).containsExactly("ok");
    }

    @Test
    public void testRawSchemaAndSchemaClassAreMutuallyExclusive()
    {
        assertThatThrownBy(() -> buildServer(RawAndClassEndpoint.class).close())
                .hasStackTraceContaining("has both rawSchema and schemaClass defined");
    }

    @Test
    public void testSchemaClassAndSchemaClassNameAreMutuallyExclusive()
    {
        assertThatThrownBy(() -> buildServer(ClassAndClassNameEndpoint.class).close())
                .hasStackTraceContaining("has both schemaClass and");
    }

    @Test
    public void testRawSchemaMustBeAnObject()
    {
        assertThatThrownBy(() -> buildServer(NonObjectRawSchemaEndpoint.class).close())
                .hasStackTraceContaining("rawSchema must be an object");
    }

    @Test
    public void testMalformedRawSchemaIsRejected()
    {
        assertThatThrownBy(() -> buildServer(MalformedRawSchemaEndpoint.class).close())
                .hasStackTraceContaining("Could not parse rawSchema");
    }

    @Test
    public void testUnknownSchemaClassNameIsRejected()
    {
        assertThatThrownBy(() -> buildServer(UnknownClassNameEndpoint.class).close())
                .hasStackTraceContaining("Could not load schema class");
    }

    private Tool tool(String name)
    {
        Tool tool = goodTools().get(name);
        assertThat(tool).as("tool %s should exist", name).isNotNull();
        return tool;
    }

    private Map<String, Tool> goodTools()
    {
        if (goodTools == null) {
            TestingServer server = closer.register(buildServer(SchemaEndpoints.class));
            String baseUri = server.injector().getInstance(TestingHttpServer.class).getBaseUrl().toString();
            TestingClient client = buildClient(closer, baseUri, "tester");
            goodTools = client.mcpClient().listTools().tools().stream()
                    .collect(toImmutableMap(Tool::name, identity()));
        }
        return goodTools;
    }

    private static TestingServer buildServer(Class<?> endpointsClass)
    {
        Function<McpModule.Builder, Module> applicator = builder -> builder
                .withIdentityMapper(TestingIdentity.class, binding -> binding.to(TestingIdentityMapper.class).in(SINGLETON))
                .withStorage(binding -> binding.to(MemoryStorageController.class).in(SINGLETON))
                .withLegacyBindings().withSessions(binding -> binding.to(StandardSessionController.class).in(SINGLETON))
                .withAllInClass(endpointsClass)
                .build();
        return new TestingServer(ImmutableMap.of(), Optional.empty(), applicator);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> property(Map<String, Object> schema, String name)
    {
        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
        return (Map<String, Object>) properties.get(name);
    }

    @SuppressWarnings("unchecked")
    private static Set<String> propertyNames(Map<String, Object> schema)
    {
        return ((Map<String, Object>) schema.get("properties")).keySet();
    }

    private static Map<String, Object> schemaWithoutDescription(Map<String, Object> schema)
    {
        Map<String, Object> copy = new HashMap<>(schema);
        copy.remove("description");
        return copy;
    }

    @SuppressWarnings("unused")
    public static class SchemaEndpoints
    {
        @McpTool(name = "no-override", description = "default schemas")
        public String noOverride(int a, int b)
        {
            return "ok";
        }

        @McpTool(name = "raw-input", description = "raw input schema", inputSchema = @McpSchema(rawSchema = RAW_SCHEMA))
        public String rawInput(int ignored)
        {
            return "ok";
        }

        @McpTool(name = "class-input", description = "class input schema", inputSchema = @McpSchema(schemaClass = CustomInput.class))
        public String classInput(int ignored)
        {
            return "ok";
        }

        @McpTool(name = "classname-input", description = "class-name input schema", inputSchema = @McpSchema(schemaClassName = CUSTOM_INPUT_CLASS_NAME))
        public String classNameInput(int ignored)
        {
            return "ok";
        }

        @McpTool(name = "raw-output", description = "raw output schema", outputSchema = @McpSchema(rawSchema = RAW_SCHEMA))
        public String rawOutput()
        {
            return "ok";
        }

        @McpTool(name = "class-output", description = "class output schema", outputSchema = @McpSchema(schemaClass = CustomOutput.class))
        public String classOutput()
        {
            return "ok";
        }
    }

    public static class RawAndClassEndpoint
    {
        @McpTool(name = "raw-and-class", description = "invalid", inputSchema = @McpSchema(rawSchema = RAW_SCHEMA, schemaClass = CustomInput.class))
        public String rawAndClass()
        {
            return "ok";
        }
    }

    public static class ClassAndClassNameEndpoint
    {
        @McpTool(name = "class-and-classname", description = "invalid", inputSchema = @McpSchema(schemaClass = CustomInput.class, schemaClassName = CUSTOM_INPUT_CLASS_NAME))
        public String classAndClassName()
        {
            return "ok";
        }
    }

    public static class NonObjectRawSchemaEndpoint
    {
        @McpTool(name = "non-object-raw", description = "invalid", inputSchema = @McpSchema(rawSchema = "123"))
        public String nonObjectRaw()
        {
            return "ok";
        }
    }

    public static class MalformedRawSchemaEndpoint
    {
        @McpTool(name = "malformed-raw", description = "invalid", inputSchema = @McpSchema(rawSchema = "{not valid json"))
        public String malformedRaw()
        {
            return "ok";
        }
    }

    public static class UnknownClassNameEndpoint
    {
        @McpTool(name = "unknown-classname", description = "invalid", inputSchema = @McpSchema(schemaClassName = "io.airlift.mcp.DoesNotExist"))
        public String unknownClassName()
        {
            return "ok";
        }
    }
}
