package io.airlift.mcp.reference;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Binder;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import io.airlift.mcp.McpMetadata;
import io.airlift.mcp.handler.RequestContextProvider;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper;
import io.modelcontextprotocol.json.schema.JsonSchemaValidator;
import io.modelcontextprotocol.json.schema.jackson.DefaultJsonSchemaValidator;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServer.StatelessSyncSpecification;
import io.modelcontextprotocol.server.McpStatelessSyncServer;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.util.DefaultMcpUriTemplateManagerFactory;
import io.modelcontextprotocol.util.McpUriTemplateManagerFactory;
import jakarta.servlet.Filter;

import static com.google.inject.Scopes.SINGLETON;
import static com.google.inject.multibindings.Multibinder.newSetBinder;

public class ReferenceModule
        implements Module
{
    @Override
    public void configure(Binder binder)
    {
        bindReferenceServer(binder);
        binder.bind(ReferenceServerTransport.class).asEagerSingleton();
        binder.bind(io.airlift.mcp.McpServer.class).to(ReferenceServer.class).in(SINGLETON);
        newSetBinder(binder, Filter.class).addBinding().to(ReferenceFilter.class).in(SINGLETON);
        binder.bind(McpUriTemplateManagerFactory.class).to(DefaultMcpUriTemplateManagerFactory.class).in(SINGLETON);
        binder.bind(RequestContextProvider.class).to(ReferenceRequestContextProvider.class).in(SINGLETON);
    }

    // bound via method so users can override the binding if needed
    protected void bindReferenceServer(Binder binder)
    {
        binder.bind(ReferenceServer.class).asEagerSingleton();
    }

    @Singleton
    @Provides
    public McpStatelessSyncServer buildServer(ReferenceServerTransport transport, McpMetadata metadata, McpJsonMapper mcpJsonMapper, McpUriTemplateManagerFactory uriTemplateManagerFactory, JsonSchemaValidator jsonSchemaValidator)
    {
        McpSchema.ServerCapabilities serverCapabilities = new McpSchema.ServerCapabilities(
                null,
                null,
                null,
                metadata.prompts() ? new McpSchema.ServerCapabilities.PromptCapabilities(false) : null,
                metadata.resources() ? new McpSchema.ServerCapabilities.ResourceCapabilities(false, false) : null,
                metadata.tools() ? new McpSchema.ServerCapabilities.ToolCapabilities(false) : null);

        StatelessSyncSpecification builder = McpServer.sync(transport)
                .jsonMapper(mcpJsonMapper)
                .capabilities(serverCapabilities)
                .uriTemplateManagerFactory(uriTemplateManagerFactory)
                .jsonSchemaValidator(jsonSchemaValidator)
                .serverInfo(metadata.implementation().name(), metadata.implementation().version());

        metadata.instructions().map(builder::instructions);

        return builder.build();
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
