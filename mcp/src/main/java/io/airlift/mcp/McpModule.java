package io.airlift.mcp;

import com.google.inject.binder.LinkedBindingBuilder;
import io.airlift.jsonrpc.JsonRpcModule;
import io.airlift.mcp.internal.InternalMcpModule;
import io.airlift.mcp.model.PaginationMetadata;
import io.airlift.mcp.session.SessionController;
import io.airlift.mcp.session.SessionMetadata;

import java.util.function.Consumer;

public interface McpModule
{
    interface Builder
            extends JsonRpcModule.Builder<Builder>
    {
        Builder withServerInfo(String serverName, String serverVersion, String instructions);

        Builder addAllInClass(Class<?> clazz);

        Builder withSessionHandling(SessionMetadata sessionMetadata, Consumer<LinkedBindingBuilder<SessionController>> binding);

        Builder withPaginationMetadata(PaginationMetadata paginationMetadata);
    }

    static Builder builder()
    {
        return InternalMcpModule.builder();
    }
}
