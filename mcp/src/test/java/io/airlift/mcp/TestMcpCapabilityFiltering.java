package io.airlift.mcp;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Closer;
import io.airlift.http.server.testing.TestingHttpServer;
import io.airlift.mcp.McpIdentity.Authenticated;
import io.airlift.mcp.model.CallToolResult;
import io.airlift.mcp.model.CompleteReference;
import io.airlift.mcp.model.CompleteRequest.CompleteArgument;
import io.airlift.mcp.model.Content.TextContent;
import io.airlift.mcp.model.GetPromptResult;
import io.airlift.mcp.model.Prompt;
import io.airlift.mcp.model.Resource;
import io.airlift.mcp.model.ResourceContents;
import io.airlift.mcp.model.ResourceTemplate;
import io.airlift.mcp.model.ResourceTemplateValues;
import io.airlift.mcp.model.Role;
import io.airlift.mcp.model.Tool;
import io.airlift.mcp.sessions.MemorySessionController;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CompleteRequest;
import io.modelcontextprotocol.spec.McpSchema.CompleteResult;
import io.modelcontextprotocol.spec.McpSchema.GetPromptRequest;
import io.modelcontextprotocol.spec.McpSchema.ListPromptsResult;
import io.modelcontextprotocol.spec.McpSchema.ListResourceTemplatesResult;
import io.modelcontextprotocol.spec.McpSchema.ListResourcesResult;
import io.modelcontextprotocol.spec.McpSchema.ListToolsResult;
import io.modelcontextprotocol.spec.McpSchema.PromptReference;
import io.modelcontextprotocol.spec.McpSchema.ReadResourceRequest;
import io.modelcontextprotocol.spec.McpSchema.ReadResourceResult;
import io.modelcontextprotocol.spec.McpSchema.ResourceReference;
import io.modelcontextprotocol.spec.McpSchema.SubscribeRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.inject.Scopes.SINGLETON;
import static io.airlift.mcp.TestMcp.assertMcpError;
import static io.airlift.mcp.TestingIdentityMapper.ANOTHER_IDENTITY;
import static io.airlift.mcp.TestingIdentityMapper.EXPECTED_IDENTITY;
import static io.modelcontextprotocol.spec.McpSchema.ErrorCodes.INVALID_PARAMS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class TestMcpCapabilityFiltering
{
    private final Closer closer = Closer.create();

    @AfterEach
    public void teardown()
            throws IOException
    {
        closer.close();
    }

    @Test
    public void testNoFilterAllowsAll()
    {
        // Setup MCP server without filter to get the default 'allow all' filter
        TestingServer testingServer = new TestingServer(
                ImmutableMap.of(),
                Optional.empty(),
                builder -> builder
                        .withIdentityMapper(TestingIdentity.class, binding -> binding.to(TestingIdentityMapper.class).in(SINGLETON))
                        .withSessions(binding -> binding.to(MemorySessionController.class).in(SINGLETON))
                        .withAllInClass(TestFilteringEnabledEndpoints.class)
                        .build());
        closer.register(testingServer);

        String baseUri = testingServer.injector().getInstance(TestingHttpServer.class).getBaseUrl().toString();
        TestingClient client = TestingClient.buildClient(closer, baseUri, "testUser");

        // Verify all tools are visible
        ListToolsResult tools = client.mcpClient().listTools();
        assertThat(tools.tools().stream().map(McpSchema.Tool::name).collect(toImmutableList()))
                .containsExactlyInAnyOrder("public-tool", "admin-tool");

        // Verify all prompts are visible
        ListPromptsResult prompts = client.mcpClient().listPrompts();
        assertThat(prompts.prompts().stream().map(McpSchema.Prompt::name).collect(toImmutableList()))
                .containsExactlyInAnyOrder("public-prompt", "admin-prompt");

        // Verify all resources are visible
        ListResourcesResult resources = client.mcpClient().listResources();
        assertThat(resources.resources().stream().map(McpSchema.Resource::uri).collect(toImmutableList()))
                .containsExactlyInAnyOrder("file://public-resource.txt", "file://admin-resource.txt");

        // Verify all resource templates are visible
        ListResourceTemplatesResult resourceTemplates = client.mcpClient().listResourceTemplates();
        assertThat(resourceTemplates.resourceTemplates().stream().map(McpSchema.ResourceTemplate::uriTemplate).collect(toImmutableList()))
                .containsExactlyInAnyOrder("file://public/{name}", "file://admin/{name}");

        // Verify all tools can be invoked
        McpSchema.CallToolResult publicToolResult = client.mcpClient().callTool(new CallToolRequest("public-tool", ImmutableMap.of()));
        assertThat(publicToolResult.content()).hasSize(1);

        McpSchema.CallToolResult adminToolResult = client.mcpClient().callTool(new CallToolRequest("admin-tool", ImmutableMap.of()));
        assertThat(adminToolResult.content()).hasSize(1);
    }

    @Test
    public void testFilteringTools()
    {
        // Setup MCP server with role-based filter
        TestingServer testingServer = new TestingServer(
                ImmutableMap.of(),
                Optional.empty(),
                builder -> builder
                        .withIdentityMapper(TestingIdentity.class, binding -> binding.to(TestingIdentityMapper.class).in(SINGLETON))
                        .withSessions(binding -> binding.to(MemorySessionController.class).in(SINGLETON))
                        .withCapabilityFilter(binding -> binding.toInstance(new TestMcpCapabilityFilter()))
                        .withAllInClass(TestFilteringEnabledEndpoints.class)
                        .build());
        closer.register(testingServer);

        String baseUri = testingServer.injector().getInstance(TestingHttpServer.class).getBaseUrl().toString();
        TestingClient client = TestingClient.buildClient(closer, baseUri, ANOTHER_IDENTITY, ANOTHER_IDENTITY);

        // Verify only public tools are visible
        ListToolsResult tools = client.mcpClient().listTools();
        assertThat(tools.tools().stream().map(McpSchema.Tool::name).collect(toImmutableList()))
                .containsOnly("public-tool");

        // Verify public tool can be invoked, but admin cannot
        McpSchema.CallToolResult publicToolResult = client.mcpClient().callTool(new CallToolRequest("public-tool", ImmutableMap.of()));
        assertThat(publicToolResult.content()).hasSize(1);
        McpSchema.CallToolResult adminToolResult = client.mcpClient().callTool(new CallToolRequest("admin-tool", ImmutableMap.of()));
        assertThat(adminToolResult.content()).hasSize(1).containsOnly(new McpSchema.TextContent("Tool not allowed: admin-tool"));
        assertThat(adminToolResult.isError()).isTrue();

        // Verify all tools are visible to admin
        TestingClient adminClient = TestingClient.buildClient(closer, baseUri, EXPECTED_IDENTITY, EXPECTED_IDENTITY);
        tools = adminClient.mcpClient().listTools();
        assertThat(tools.tools().stream().map(McpSchema.Tool::name).collect(toImmutableList()))
                .containsExactlyInAnyOrder("public-tool", "admin-tool");

        // Verify all tools can be invoked by admin
        publicToolResult = adminClient.mcpClient().callTool(new CallToolRequest("public-tool", ImmutableMap.of()));
        assertThat(publicToolResult.content()).hasSize(1);

        adminToolResult = adminClient.mcpClient().callTool(new CallToolRequest("admin-tool", ImmutableMap.of()));
        assertThat(adminToolResult.content()).hasSize(1);
    }

    @Test
    public void testFilteringPrompts()
    {
        // Setup MCP server with role-based filter
        TestingServer testingServer = new TestingServer(
                ImmutableMap.of(),
                Optional.empty(),
                builder -> builder
                        .withIdentityMapper(TestingIdentity.class, binding -> binding.to(TestingIdentityMapper.class).in(SINGLETON))
                        .withSessions(binding -> binding.to(MemorySessionController.class).in(SINGLETON))
                        .withCapabilityFilter(binding -> binding.toInstance(new TestMcpCapabilityFilter()))
                        .withAllInClass(TestFilteringEnabledEndpoints.class)
                        .build());
        closer.register(testingServer);

        String baseUri = testingServer.injector().getInstance(TestingHttpServer.class).getBaseUrl().toString();
        TestingClient client = TestingClient.buildClient(closer, baseUri, ANOTHER_IDENTITY, ANOTHER_IDENTITY);

        // Verify only public prompts are visible
        ListPromptsResult prompts = client.mcpClient().listPrompts();
        assertThat(prompts.prompts().stream().map(McpSchema.Prompt::name).collect(toImmutableList()))
                .containsOnly("public-prompt");

        // Verify public prompt can be retrieved, but admin can't
        McpSchema.GetPromptResult publicPromptResult = client.mcpClient().getPrompt(new GetPromptRequest("public-prompt", ImmutableMap.of()));
        assertThat(publicPromptResult.messages()).hasSize(1);
        assertThatThrownBy(() -> client.mcpClient().getPrompt(new GetPromptRequest("admin-prompt", ImmutableMap.of())))
                .satisfies(e -> assertMcpError(e, INVALID_PARAMS, "Prompt not allowed: admin-prompt"));

        // Verify all tools are visible to admin
        TestingClient adminClient = TestingClient.buildClient(closer, baseUri, EXPECTED_IDENTITY, EXPECTED_IDENTITY);
        prompts = adminClient.mcpClient().listPrompts();
        assertThat(prompts.prompts().stream().map(McpSchema.Prompt::name).collect(toImmutableList()))
                .containsExactlyInAnyOrder("public-prompt", "admin-prompt");

        // Verify all tools can be invoked by admin
        publicPromptResult = adminClient.mcpClient().getPrompt(new GetPromptRequest("public-prompt", ImmutableMap.of()));
        assertThat(publicPromptResult.messages()).hasSize(1);

        McpSchema.GetPromptResult adminPromptResult = adminClient.mcpClient().getPrompt(new GetPromptRequest("admin-prompt", ImmutableMap.of()));
        assertThat(adminPromptResult.messages()).hasSize(1);
    }

    @Test
    public void testFilteringResources()
    {
        // Setup MCP server with role-based filter
        TestingServer testingServer = new TestingServer(
                ImmutableMap.of(),
                Optional.empty(),
                builder -> builder
                        .withIdentityMapper(TestingIdentity.class, binding -> binding.to(TestingIdentityMapper.class).in(SINGLETON))
                        .withSessions(binding -> binding.to(MemorySessionController.class).in(SINGLETON))
                        .withCapabilityFilter(binding -> binding.toInstance(new TestMcpCapabilityFilter()))
                        .withAllInClass(TestFilteringEnabledEndpoints.class)
                        .build());
        closer.register(testingServer);

        String baseUri = testingServer.injector().getInstance(TestingHttpServer.class).getBaseUrl().toString();
        TestingClient client = TestingClient.buildClient(closer, baseUri, ANOTHER_IDENTITY, ANOTHER_IDENTITY);

        // Verify only public resources are visible
        ListResourcesResult resources = client.mcpClient().listResources();
        assertThat(resources.resources().stream().map(McpSchema.Resource::uri).collect(toImmutableList()))
                .containsExactly("file://public-resource.txt");

        // Verify public resource can be read, but admin can't
        ReadResourceResult publicResourceResult = client.mcpClient().readResource(new ReadResourceRequest("file://public-resource.txt"));
        assertThat(publicResourceResult.contents()).hasSize(1);
        assertThatThrownBy(() -> client.mcpClient().readResource(new ReadResourceRequest("file://admin-resource.txt")))
                .satisfies(e -> assertMcpError(e, INVALID_PARAMS, "Resource access not allowed: file://admin-resource.txt"));

        // Verify public resource can be subscribed, but admin can't
        client.mcpClient().subscribeResource(new SubscribeRequest("file://public-resource.txt"));
        assertThatThrownBy(() -> client.mcpClient().subscribeResource(new SubscribeRequest("file://admin-resource.txt")))
                .satisfies(e -> assertMcpError(e, INVALID_PARAMS, "Resource access not allowed: file://admin-resource.txt"));

        // Verify all tools are visible to admin
        TestingClient adminClient = TestingClient.buildClient(closer, baseUri, EXPECTED_IDENTITY, EXPECTED_IDENTITY);
        resources = adminClient.mcpClient().listResources();
        assertThat(resources.resources().stream().map(McpSchema.Resource::uri).collect(toImmutableList()))
                .containsExactlyInAnyOrder("file://public-resource.txt", "file://admin-resource.txt");

        publicResourceResult = adminClient.mcpClient().readResource(new ReadResourceRequest("file://public-resource.txt"));
        assertThat(publicResourceResult.contents()).hasSize(1);

        McpSchema.ReadResourceResult adminResourceResult = adminClient.mcpClient().readResource(new ReadResourceRequest("file://public-resource.txt"));
        assertThat(adminResourceResult.contents()).hasSize(1);

        // Verify admin can subscribe to all
        adminClient.mcpClient().subscribeResource(new SubscribeRequest("file://public-resource.txt"));
        adminClient.mcpClient().subscribeResource(new SubscribeRequest("file://admin-resource.txt"));
    }

    @Test
    public void testFilteringResourceTemplates()
    {
        // Setup MCP server with role-based filter
        TestingServer testingServer = new TestingServer(
                ImmutableMap.of(),
                Optional.empty(),
                builder -> builder
                        .withIdentityMapper(TestingIdentity.class, binding -> binding.to(TestingIdentityMapper.class).in(SINGLETON))
                        .withSessions(binding -> binding.to(MemorySessionController.class).in(SINGLETON))
                        .withCapabilityFilter(binding -> binding.toInstance(new TestMcpCapabilityFilter()))
                        .withAllInClass(TestFilteringEnabledEndpoints.class)
                        .build());
        closer.register(testingServer);

        String baseUri = testingServer.injector().getInstance(TestingHttpServer.class).getBaseUrl().toString();
        TestingClient client = TestingClient.buildClient(closer, baseUri, ANOTHER_IDENTITY, ANOTHER_IDENTITY);

        // Verify only public resource templates are visible
        ListResourceTemplatesResult resourceTemplates = client.mcpClient().listResourceTemplates();
        assertThat(resourceTemplates.resourceTemplates().stream().map(McpSchema.ResourceTemplate::uriTemplate).collect(toImmutableList()))
                .containsOnly("file://public/{name}");

        // Verify public resource template can be read, but admin cannot
        ReadResourceResult publicTemplateResult = client.mcpClient().readResource(new ReadResourceRequest("file://public/test.txt"));
        assertThat(publicTemplateResult.contents()).hasSize(1);
        assertThatThrownBy(() -> client.mcpClient().readResource(new ReadResourceRequest("file://admin/test.txt")))
                .satisfies(e -> assertMcpError(e, INVALID_PARAMS, "Resource access not allowed: file://admin/test.txt"));

        // Verify all tools are visible to admin
        TestingClient adminClient = TestingClient.buildClient(closer, baseUri, EXPECTED_IDENTITY, EXPECTED_IDENTITY);
        resourceTemplates = adminClient.mcpClient().listResourceTemplates();
        assertThat(resourceTemplates.resourceTemplates().stream().map(McpSchema.ResourceTemplate::uriTemplate).collect(toImmutableList()))
                .containsExactlyInAnyOrder("file://public/{name}", "file://admin/{name}");

        // Verify all tools can be invoked by admin
        publicTemplateResult = adminClient.mcpClient().readResource(new ReadResourceRequest("file://public/test.txt"));
        assertThat(publicTemplateResult.contents()).hasSize(1);

        ReadResourceResult adminResourceResult = adminClient.mcpClient().readResource(new ReadResourceRequest("file://admin/test.txt"));
        assertThat(adminResourceResult.contents()).hasSize(1);
    }

    @Test
    public void testFilteringPromptCompletions()
    {
        // Setup MCP server with role-based filter
        TestingServer testingServer = new TestingServer(
                ImmutableMap.of(),
                Optional.empty(),
                builder -> builder
                        .withIdentityMapper(TestingIdentity.class, binding -> binding.to(TestingIdentityMapper.class).in(SINGLETON))
                        .withSessions(binding -> binding.to(MemorySessionController.class).in(SINGLETON))
                        .withCapabilityFilter(binding -> binding.toInstance(new TestMcpCapabilityFilter()))
                        .withAllInClass(TestFilteringEnabledEndpoints.class)
                        .build());
        closer.register(testingServer);

        String baseUri = testingServer.injector().getInstance(TestingHttpServer.class).getBaseUrl().toString();
        TestingClient client = TestingClient.buildClient(closer, baseUri, ANOTHER_IDENTITY, ANOTHER_IDENTITY);

        // Verify non-admin can get completions for public prompt
        CompleteRequest publicPromptCompleteRequest = new CompleteRequest(
                new PromptReference("public-prompt"),
                new McpSchema.CompleteRequest.CompleteArgument("arg", ""));
        CompleteResult publicPromptResult = client.mcpClient().completeCompletion(publicPromptCompleteRequest);
        assertThat(publicPromptResult.completion().values()).hasSize(2);

        // Verify non-admin gets empty completions for admin prompt (filtered out)
        CompleteRequest adminPromptCompleteRequest = new CompleteRequest(
                new PromptReference("admin-prompt"),
                new McpSchema.CompleteRequest.CompleteArgument("arg", ""));
        CompleteResult adminPromptResult = client.mcpClient().completeCompletion(adminPromptCompleteRequest);
        assertThat(adminPromptResult.completion().values()).isEmpty();

        // Verify non-admin can get completions for public resource template
        CompleteRequest publicResourceCompleteRequest = new CompleteRequest(
                new ResourceReference("file://public/{name}"),
                new McpSchema.CompleteRequest.CompleteArgument("name", ""));
        CompleteResult publicResourceResult = client.mcpClient().completeCompletion(publicResourceCompleteRequest);
        assertThat(publicResourceResult.completion().values()).hasSize(2);

        // Verify non-admin gets empty completions for admin resource template (filtered out)
        CompleteRequest adminResourceCompleteRequest = new CompleteRequest(
                new ResourceReference("file://admin/{name}"),
                new McpSchema.CompleteRequest.CompleteArgument("name", ""));
        CompleteResult adminResourceResult = client.mcpClient().completeCompletion(adminResourceCompleteRequest);
        assertThat(adminResourceResult.completion().values()).isEmpty();

        TestingClient adminClient = TestingClient.buildClient(closer, baseUri, EXPECTED_IDENTITY, EXPECTED_IDENTITY);

        // Verify admin can get completions for admin prompt
        adminPromptResult = adminClient.mcpClient().completeCompletion(adminPromptCompleteRequest);
        assertThat(adminPromptResult.completion().values()).hasSize(2);

        // Verify admin can get completions for admin resource template
        adminResourceResult = adminClient.mcpClient().completeCompletion(adminResourceCompleteRequest);
        assertThat(adminResourceResult.completion().values()).hasSize(2);
    }

    private static class TestMcpCapabilityFilter
            implements McpCapabilityFilter
    {
        TestMcpCapabilityFilter() {}

        @Override
        public boolean isAllowed(Authenticated<?> identity, Tool tool)
        {
            TestingIdentity actualIdentity = (TestingIdentity) identity.identity();
            return EXPECTED_IDENTITY.equals(actualIdentity.name()) || tool.name().equals("public-tool");
        }

        @Override
        public boolean isAllowed(Authenticated<?> identity, Prompt prompt)
        {
            TestingIdentity actualIdentity = (TestingIdentity) identity.identity();
            return EXPECTED_IDENTITY.equals(actualIdentity.name()) || prompt.name().equals("public-prompt");
        }

        @Override
        public boolean isAllowed(Authenticated<?> identity, Resource resource)
        {
            TestingIdentity actualIdentity = (TestingIdentity) identity.identity();
            return EXPECTED_IDENTITY.equals(actualIdentity.name()) || resource.uri().contains("public");
        }

        @Override
        public boolean isAllowed(Authenticated<?> identity, ResourceTemplate resourceTemplate)
        {
            TestingIdentity actualIdentity = (TestingIdentity) identity.identity();
            return EXPECTED_IDENTITY.equals(actualIdentity.name()) || resourceTemplate.uriTemplate().contains("public");
        }

        @Override
        public boolean isAllowed(Authenticated<?> identity, CompleteReference reference)
        {
            TestingIdentity actualIdentity = (TestingIdentity) identity.identity();
            return EXPECTED_IDENTITY.equals(actualIdentity.name()) || switch (reference) {
                case CompleteReference.PromptReference promptReference -> promptReference.name().contains("public");
                case CompleteReference.ResourceReference resourceReference -> resourceReference.uri().contains("public");
            };
        }

        @Override
        public boolean isAllowed(Authenticated<?> identity, String resourceUri)
        {
            TestingIdentity actualIdentity = (TestingIdentity) identity.identity();
            return EXPECTED_IDENTITY.equals(actualIdentity.name()) || resourceUri.contains("public");
        }
    }

    public static class TestFilteringEnabledEndpoints
    {
        @McpTool(name = "public-tool", description = "Available to all")
        public CallToolResult publicTool()
        {
            return new CallToolResult(ImmutableList.of(new TextContent("public")), Optional.empty(), false);
        }

        @McpTool(name = "admin-tool", description = "Admin only")
        public CallToolResult adminTool()
        {
            return new CallToolResult(ImmutableList.of(new TextContent("admin")), Optional.empty(), false);
        }

        @McpPrompt(name = "public-prompt", description = "Available to all")
        public GetPromptResult publicPrompt()
        {
            return new GetPromptResult(
                    Optional.of("Public prompt"),
                    ImmutableList.of(new GetPromptResult.PromptMessage(Role.USER, new TextContent("Public prompt content"))));
        }

        @McpPrompt(name = "admin-prompt", description = "Admin only")
        public GetPromptResult adminPrompt()
        {
            return new GetPromptResult(
                    Optional.of("Admin prompt"),
                    ImmutableList.of(new GetPromptResult.PromptMessage(Role.USER, new TextContent("Admin prompt content"))));
        }

        @McpResource(name = "public-resource", uri = "file://public-resource.txt", description = "Available to all", mimeType = "text/plain")
        public ResourceContents publicResource()
        {
            return new ResourceContents("public-resource", "file://public-resource.txt", "text/plain", "Public resource content");
        }

        @McpResource(name = "admin-resource", uri = "file://admin-resource.txt", description = "Admin only", mimeType = "text/plain")
        public ResourceContents adminResource()
        {
            return new ResourceContents("admin-resource", "file://admin-resource.txt", "text/plain", "Admin resource content");
        }

        @McpResourceTemplate(name = "public-template", uriTemplate = "file://public/{name}", description = "Available to all", mimeType = "text/plain")
        public List<ResourceContents> publicResourceTemplate(io.airlift.mcp.model.ReadResourceRequest request, ResourceTemplateValues resourceTemplateValues)
        {
            String name = resourceTemplateValues.templateValues().getOrDefault("name", "unknown");
            return ImmutableList.of(new ResourceContents("public-" + name, request.uri(), "text/plain", "Public template: " + name));
        }

        @McpResourceTemplate(name = "admin-template", uriTemplate = "file://admin/{name}", description = "Admin only", mimeType = "text/plain")
        public List<ResourceContents> adminResourceTemplate(io.airlift.mcp.model.ReadResourceRequest request, ResourceTemplateValues resourceTemplateValues)
        {
            String name = resourceTemplateValues.templateValues().getOrDefault("name", "unknown");
            return ImmutableList.of(new ResourceContents("admin-" + name, request.uri(), "text/plain", "Admin template: " + name));
        }

        @McpPromptCompletion(name = "public-prompt")
        public List<String> publicPromptCompletions(CompleteArgument argument)
        {
            return ImmutableList.of("public-completion-1", "public-completion-2");
        }

        @McpPromptCompletion(name = "admin-prompt")
        public List<String> adminPromptCompletions(CompleteArgument argument)
        {
            return ImmutableList.of("admin-completion-1", "admin-completion-2");
        }

        @McpResourceTemplateCompletion(uriTemplate = "file://public/{name}")
        public List<String> publicResourceTemplateCompletions(CompleteArgument argument)
        {
            if (argument.name().equals("name")) {
                return ImmutableList.of("public-file-1.txt", "public-file-2.txt");
            }
            return ImmutableList.of();
        }

        @McpResourceTemplateCompletion(uriTemplate = "file://admin/{name}")
        public List<String> adminResourceTemplateCompletions(CompleteArgument argument)
        {
            if (argument.name().equals("name")) {
                return ImmutableList.of("admin-file-1.txt", "admin-file-2.txt");
            }
            return ImmutableList.of();
        }
    }
}
