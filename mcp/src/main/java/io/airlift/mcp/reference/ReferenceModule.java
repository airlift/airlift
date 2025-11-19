package io.airlift.mcp.reference;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Binder;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import io.airlift.mcp.McpMetadata;
import io.airlift.mcp.handler.RequestContextProvider;
import io.airlift.mcp.sessions.SessionController;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper;
import io.modelcontextprotocol.json.schema.JsonSchemaValidator;
import io.modelcontextprotocol.json.schema.jackson.DefaultJsonSchemaValidator;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServer.StatelessSyncSpecification;
import io.modelcontextprotocol.server.McpStatelessSyncServer;
import io.modelcontextprotocol.spec.McpSchema.ServerCapabilities;
import io.modelcontextprotocol.spec.McpSchema.ServerCapabilities.LoggingCapabilities;
import io.modelcontextprotocol.spec.McpSchema.ServerCapabilities.PromptCapabilities;
import io.modelcontextprotocol.spec.McpSchema.ServerCapabilities.ResourceCapabilities;
import io.modelcontextprotocol.spec.McpSchema.ServerCapabilities.ToolCapabilities;
import io.modelcontextprotocol.util.DefaultMcpUriTemplateManagerFactory;
import io.modelcontextprotocol.util.McpUriTemplateManagerFactory;
import jakarta.servlet.Filter;

import java.util.Optional;

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
    public McpStatelessSyncServer buildServer(ReferenceServerTransport transport, McpMetadata metadata, McpJsonMapper mcpJsonMapper, McpUriTemplateManagerFactory uriTemplateManagerFactory, JsonSchemaValidator jsonSchemaValidator, Optional<SessionController> sessionController)
    {
        boolean sessionsEnabled = sessionController.isPresent();

        ServerCapabilities serverCapabilities = new ServerCapabilities(
                null,
                null,
                sessionsEnabled ? new LoggingCapabilities() : null,
                metadata.prompts() ? new PromptCapabilities(false) : null,
                metadata.resources() ? new ResourceCapabilities(false, false) : null,
                metadata.tools() ? new ToolCapabilities(false) : null);

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
