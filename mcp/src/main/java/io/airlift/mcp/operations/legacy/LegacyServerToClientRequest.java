package io.airlift.mcp.operations.legacy;

import io.airlift.mcp.McpRequestContext;
import io.airlift.mcp.model.JsonRpcResponse;

import java.time.Duration;
import java.util.concurrent.TimeoutException;

public class LegacyServerToClientRequest
{
    /**
     * Sends a server-to-client request and waits for the response until given timeout. NOTE - may not be supported depending on how you've configured MCP - see the README regarding Sessions and Storage
     */
    public <R> JsonRpcResponse<R> serverToClientRequest(McpRequestContext requestContext, String method, Object params, Class<R> responseType, Duration timeout, Duration pollInterval)
            throws InterruptedException, TimeoutException
    {
        if (requestContext instanceof LegacyRequestContextImpl requestContextImpl) {
            return requestContextImpl.serverToClientRequest(method, params, responseType, timeout, pollInterval);
        }

        throw new UnsupportedOperationException("serverToClientRequest is not supported");
    }
}
