package io.airlift.mcp;

import io.airlift.mcp.handler.CompletionHandler;
import io.airlift.mcp.handler.PromptHandler;
import io.airlift.mcp.handler.ResourceHandler;
import io.airlift.mcp.handler.ResourceTemplateHandler;
import io.airlift.mcp.handler.ToolHandler;
import io.airlift.mcp.model.CompleteReference;
import io.airlift.mcp.model.Prompt;
import io.airlift.mcp.model.Resource;
import io.airlift.mcp.model.ResourceTemplate;
import io.airlift.mcp.model.Tool;

public interface McpServer
{
    void stop();

    void addTool(Tool tool, ToolHandler toolHandler);

    void removeTool(String toolName);

    /**
     * <p>Change/update the current global version of the server's tools list.
     * This should be treated in a similar manner to a database schema migration.
     * Depending on your {@link io.airlift.mcp.sessions.SessionController} implementation,
     * this change will be reflected in all active sessions in all running server
     * instances.</p>
     *
     * <p>{@code newVersion} is an opaque value that is defined by your application.
     * It can be a numeric version, a timestamp, a hash, etc.</p>
     */
    default void updateToolsListVersion(String newVersion)
    {
        // only implemented when sessions are configured

        throw new UnsupportedOperationException();
    }

    void addPrompt(Prompt prompt, PromptHandler promptHandler);

    void removePrompt(String promptName);

    /**
     * see {@link #updateToolsListVersion(String)} for a detailed description of version updates
     */
    default void updatePromptsListVersion(String newVersion)
    {
        // only implemented when sessions are configured

        throw new UnsupportedOperationException();
    }

    void addResource(Resource resource, ResourceHandler handler);

    void removeResource(String resourceUri);

    void addResourceTemplate(ResourceTemplate resourceTemplate, ResourceTemplateHandler handler);

    void removeResourceTemplate(String uriTemplate);

    /**
     * see {@link #updateToolsListVersion(String)} for a detailed description of version updates
     */
    default void updateResourcesListVersion(String newVersion)
    {
        // only implemented when sessions are configured

        throw new UnsupportedOperationException();
    }

    /**
     * see {@link #updateToolsListVersion(String)} for a detailed description of version updates
     */
    default void updateResourcesVersion(String uri, String newVersion)
    {
        // only implemented when sessions are configured

        throw new UnsupportedOperationException();
    }

    void addCompletion(CompleteReference reference, CompletionHandler handler);

    void removeCompletion(CompleteReference reference);
}
