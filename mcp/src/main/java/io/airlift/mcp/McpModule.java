package io.airlift.mcp;

import io.airlift.jsonrpc.JsonRpcModule;
import io.airlift.mcp.internal.InternalMcpModule;

public interface McpModule
{
    interface Builder
            extends JsonRpcModule.Builder<Builder>
    {
        Builder withServerInfo(String serverName, String serverVersion, String instructions);

        Builder addAllInClass(Class<?> clazz);
    }

    static Builder builder()
    {
        return InternalMcpModule.builder();
    }
}
