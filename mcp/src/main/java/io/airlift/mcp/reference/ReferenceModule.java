package io.airlift.mcp.reference;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import com.google.inject.Binder;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import io.airlift.mcp.McpMetadata;
import io.airlift.mcp.handler.CompletionEntry;
import io.airlift.mcp.handler.PromptEntry;
import io.airlift.mcp.handler.RequestContextProvider;
import io.airlift.mcp.handler.ResourceTemplateEntry;
import io.airlift.mcp.model.CompleteReference.PromptReference;
import io.airlift.mcp.model.CompleteReference.ResourceReference;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper;
import io.modelcontextprotocol.json.schema.JsonSchemaValidator;
import io.modelcontextprotocol.json.schema.jackson.DefaultJsonSchemaValidator;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServer.StatelessSyncSpecification;
import io.modelcontextprotocol.server.McpStatelessServerFeatures.SyncCompletionSpecification;
import io.modelcontextprotocol.server.McpStatelessSyncServer;
import io.modelcontextprotocol.server.transport.HttpServletStatelessServerTransport;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.ServerCapabilities;
import io.modelcontextprotocol.spec.McpSchema.ServerCapabilities.CompletionCapabilities;
import io.modelcontextprotocol.spec.McpSchema.ServerCapabilities.PromptCapabilities;
import io.modelcontextprotocol.spec.McpSchema.ServerCapabilities.ResourceCapabilities;
import io.modelcontextprotocol.spec.McpSchema.ServerCapabilities.ToolCapabilities;
import io.modelcontextprotocol.util.DefaultMcpUriTemplateManagerFactory;
import io.modelcontextprotocol.util.McpUriTemplateManagerFactory;
import jakarta.servlet.Filter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.inject.Scopes.SINGLETON;
import static com.google.inject.multibindings.Multibinder.newSetBinder;
import static io.airlift.mcp.McpMetadata.CONTEXT_REQUEST_KEY;
import static io.airlift.mcp.reference.Mapper.mapCompletion;

public class ReferenceModule
        implements Module
{
    @Override
    public void configure(Binder binder)
    {
        binder.bind(ReferenceServer.class).asEagerSingleton();
        binder.bind(io.airlift.mcp.McpServer.class).to(ReferenceServer.class).in(SINGLETON);
        newSetBinder(binder, Filter.class).addBinding().to(ReferenceFilter.class).in(SINGLETON);
        binder.bind(McpUriTemplateManagerFactory.class).to(DefaultMcpUriTemplateManagerFactory.class).in(SINGLETON);
        binder.bind(RequestContextProvider.class).to(ReferenceRequestContextProvider.class).in(SINGLETON);
    }

    @Singleton
    @Provides
    public HttpServletStatelessServerTransport mcpTransport(McpMetadata metadata, McpJsonMapper objectMapper)
    {
        return HttpServletStatelessServerTransport.builder()
                .messageEndpoint(metadata.uriPath())
                .jsonMapper(objectMapper)
                .contextExtractor(request -> McpTransportContext.create(ImmutableMap.of(CONTEXT_REQUEST_KEY, request)))
                .build();
    }

    @Singleton
    @Provides
    public McpStatelessSyncServer buildServer(
            HttpServletStatelessServerTransport transport,
            McpMetadata metadata,
            McpJsonMapper objectMapper,
            McpUriTemplateManagerFactory uriTemplateManagerFactory,
            RequestContextProvider requestContextProvider,
            Set<PromptEntry> prompts,
            Set<ResourceTemplateEntry> resourceTemplates,
            JsonSchemaValidator jsonSchemaValidator,
            Set<CompletionEntry> completions)
    {
        ServerCapabilities serverCapabilities = new ServerCapabilities(
                metadata.completions() ? new CompletionCapabilities() : null,
                null,
                null,
                metadata.prompts() ? new PromptCapabilities(false) : null,
                metadata.resources() ? new ResourceCapabilities(false, false) : null,
                metadata.tools() ? new ToolCapabilities(false) : null);

        StatelessSyncSpecification builder = McpServer.sync(transport)
                .jsonMapper(objectMapper)
                .capabilities(serverCapabilities)
                .uriTemplateManagerFactory(uriTemplateManagerFactory)
                .jsonSchemaValidator(jsonSchemaValidator)
                .serverInfo(metadata.implementation().name(), metadata.implementation().version());

        buildCompletions(builder, requestContextProvider, prompts, resourceTemplates, completions);

        metadata.instructions().map(builder::instructions);

        return builder.build();
    }

    private static void buildCompletions(StatelessSyncSpecification builder, RequestContextProvider requestContextProvider, Set<PromptEntry> prompts, Set<ResourceTemplateEntry> resourceTemplates, Set<CompletionEntry> completions)
    {
        if (completions.isEmpty()) {
            return;
        }

        Set<String> promptNames = prompts.stream()
                .map(entry -> entry.prompt().name())
                .collect(toImmutableSet());
        Set<String> usedPromptNames = new HashSet<>();

        Set<String> resourceTemplateUris = resourceTemplates.stream()
                .map(entry -> entry.resourceTemplate().uriTemplate())
                .collect(toImmutableSet());
        Set<String> usedResourceTemplateUris = new HashSet<>();

        List<SyncCompletionSpecification> mappedCompletions = new ArrayList<>();

        completions.stream()
                .peek(completion -> {
                    switch (completion.reference()) {
                        case PromptReference promptReference -> {
                            if (!promptNames.contains(promptReference.name())) {
                                throw new IllegalStateException("No prompt found for completion: " + promptReference.name());
                            }
                            usedPromptNames.add(promptReference.name());
                        }
                        case ResourceReference resourceReference -> {
                            if (!resourceTemplateUris.contains(resourceReference.uri())) {
                                throw new IllegalStateException("No resource template found for completion: " + resourceReference.uri());
                            }
                            usedResourceTemplateUris.add(resourceReference.uri());
                        }
                    }
                })
                .map(completion -> mapCompletion(requestContextProvider, completion.reference(), completion.handler()))
                .forEach(mappedCompletions::add);

        McpSchema.CompleteResult emptyResult = new McpSchema.CompleteResult(new McpSchema.CompleteResult.CompleteCompletion(ImmutableList.of(), 0, false));

        // the reference implementation requires that all prompts have a completion, so add empty completions for any missing prompts
        Sets.difference(promptNames, usedPromptNames)
                .stream()
                .map(missingPromptCompletion -> new SyncCompletionSpecification(new McpSchema.PromptReference(McpSchema.PromptReference.TYPE, missingPromptCompletion),
                        (_, _) -> emptyResult))
                .forEach(mappedCompletions::add);

        // the reference implementation requires that all resource templates have a completion, so add empty completions for any missing resource templates
        Sets.difference(resourceTemplateUris, usedResourceTemplateUris)
                .stream()
                .map(missingResourceTemplatetCompletion -> new SyncCompletionSpecification(new McpSchema.ResourceReference(McpSchema.ResourceReference.TYPE, missingResourceTemplatetCompletion),
                        (_, _) -> emptyResult))
                .forEach(mappedCompletions::add);

        builder.completions(mappedCompletions);
    }

    @Singleton
    @Provides
    public McpJsonMapper mcpJsonMapper(ObjectMapper objectMapper)
    {
        return new JacksonMcpJsonMapper(objectMapper);
    }

    @Singleton
    @Provides
    public JsonSchemaValidator mcpJsonSchemaValidator(ObjectMapper objectMapper)
    {
        return new DefaultJsonSchemaValidator(objectMapper);
    }
}
