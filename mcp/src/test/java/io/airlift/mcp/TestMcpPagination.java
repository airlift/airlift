package io.airlift.mcp;

import com.google.common.collect.ImmutableMap;
import com.google.common.io.Closer;
import com.google.inject.Key;
import com.google.inject.TypeLiteral;
import io.airlift.http.server.testing.TestingHttpServer;
import io.airlift.mcp.handler.PromptEntry;
import io.airlift.mcp.handler.ResourceEntry;
import io.airlift.mcp.handler.ResourceTemplateEntry;
import io.airlift.mcp.handler.ToolEntry;
import io.airlift.mcp.model.Icon;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.ListPromptsResult;
import io.modelcontextprotocol.spec.McpSchema.ListResourceTemplatesResult;
import io.modelcontextprotocol.spec.McpSchema.ListResourcesResult;
import io.modelcontextprotocol.spec.McpSchema.ListToolsResult;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.inject.Scopes.SINGLETON;
import static io.airlift.mcp.TestingClient.buildClient;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

@TestInstance(PER_CLASS)
public class TestMcpPagination
{
    public static final int PAGE_SIZE = 2;

    private final Closer closer = Closer.create();
    private final TestingClient client;
    private final List<String> tools;
    private final List<String> prompts;
    private final List<String> resources;
    private final List<String> resourcesTemplates;

    public TestMcpPagination()
    {
        TestingServer testingServer = new TestingServer(ImmutableMap.of("mcp.page-size", Integer.toString(PAGE_SIZE)), Optional.empty(), builder -> builder
                .withIdentityMapper(TestingIdentity.class, binding -> binding.to(TestingIdentityMapper.class).in(SINGLETON))
                .withAllInClass(TestingEndpoints.class)
                .addIcon("google", binding -> binding.toInstance(new Icon("https://www.gstatic.com/images/branding/searchlogo/ico/favicon.ico")))
                .build());
        closer.register(testingServer);

        String baseUri = testingServer.injector().getInstance(TestingHttpServer.class).getBaseUrl().toString();

        client = buildClient(closer, baseUri, "Client");

        tools = testingServer.injector().getInstance(Key.get(new TypeLiteral<Set<ToolEntry>>() {}))
                .stream()
                .map(toolEntry -> toolEntry.tool().name())
                .collect(toImmutableList());

        prompts = testingServer.injector().getInstance(Key.get(new TypeLiteral<Set<PromptEntry>>() {}))
                .stream()
                .map(promptEntry -> promptEntry.prompt().name())
                .collect(toImmutableList());

        resources = testingServer.injector().getInstance(Key.get(new TypeLiteral<Set<ResourceEntry>>() {}))
                .stream()
                .map(resourceEntry -> resourceEntry.resource().name())
                .collect(toImmutableList());

        resourcesTemplates = testingServer.injector().getInstance(Key.get(new TypeLiteral<Set<ResourceTemplateEntry>>() {}))
                .stream()
                .map(resourceTemplateEntry -> resourceTemplateEntry.resourceTemplate().name())
                .collect(toImmutableList());
    }

    @AfterAll
    public void shutdown()
            throws IOException
    {
        closer.close();
    }

    @AfterEach
    public void reset()
    {
        client.reset();
    }

    @Test
    public void testWithManualPagination()
    {
        asserWithManualPagination(tools, cursor -> client.mcpClient().listTools(cursor), results -> results.tools().stream().map(McpSchema.Tool::name), ListToolsResult::nextCursor);
        asserWithManualPagination(prompts, cursor -> client.mcpClient().listPrompts(cursor), results -> results.prompts().stream().map(McpSchema.Prompt::name), ListPromptsResult::nextCursor);
        asserWithManualPagination(resources, cursor -> client.mcpClient().listResources(cursor), results -> results.resources().stream().map(McpSchema.Resource::name), ListResourcesResult::nextCursor);
        asserWithManualPagination(resourcesTemplates, cursor -> client.mcpClient().listResourceTemplates(cursor), results -> results.resourceTemplates().stream().map(McpSchema.ResourceTemplate::name), ListResourceTemplatesResult::nextCursor);
    }

    @Test
    public void testWithClientPagination()
    {
        asserWithClientPagination(tools, client.mcpClient().listTools().tools(), McpSchema.Tool::name);
        asserWithClientPagination(prompts, client.mcpClient().listPrompts().prompts(), McpSchema.Prompt::name);
        asserWithClientPagination(resources, client.mcpClient().listResources().resources(), McpSchema.Resource::name);
        asserWithClientPagination(resourcesTemplates, client.mcpClient().listResourceTemplates().resourceTemplates(), McpSchema.ResourceTemplate::name);
    }

    private <R> void asserWithManualPagination(List<String> expected, Function<String, R> resultsSupplier, Function<R, Stream<String>> mapper, Function<R, String> nextCursorMapper)
    {
        String nextCursor = "";

        int pages = 0;
        List<String> queriedNames = new ArrayList<>();
        do {
            ++pages;
            R results = resultsSupplier.apply(nextCursor);
            mapper.apply(results).forEach(queriedNames::add);
            nextCursor = nextCursorMapper.apply(results);
        } while (nextCursor != null);

        assertThat(queriedNames).containsExactlyInAnyOrderElementsOf(expected);

        int exceptedPages = (expected.size() + PAGE_SIZE - 1) / PAGE_SIZE;
        assertThat(pages).isEqualTo(exceptedPages);
    }

    private <T> void asserWithClientPagination(List<String> expected, List<T> results, Function<T, String> keyMapper)
    {
        List<String> queriedNames = results.stream().map(keyMapper).collect(toImmutableList());
        assertThat(queriedNames).containsExactlyInAnyOrderElementsOf(expected);
    }
}
