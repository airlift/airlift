package io.airlift.mcp;

import com.fasterxml.jackson.core.type.TypeReference;
import io.airlift.mcp.model.Implementation;
import io.airlift.mcp.model.InitializeRequest.ClientCapabilities;
import io.airlift.mcp.model.ListRootsResult.Root;
import io.airlift.mcp.model.LoggingLevel;
import jakarta.servlet.http.HttpServletRequest;

import java.time.Duration;
import java.util.List;

public interface McpRequestContext
{
    McpServer server();

    HttpServletRequest request();

    void sendProgress(double progress, double total, String message);

    default void sendLog(LoggingLevel level, String name, Object messageOrData)
    {
        // sessions must be enabled to send logs
        throw new UnsupportedOperationException();
    }

    default <T, R> R serverToClientRequest(String method, T params, TypeReference<R> responseType, Duration timeout)
            throws McpException
    {
        // sessions must be enabled to send server-to-client requests
        throw new UnsupportedOperationException();
    }

    default ClientCapabilities clientCapabilities()
    {
        // sessions must be enabled to get client capabilities
        throw new UnsupportedOperationException();
    }

    default Implementation clientInfo()
    {
        // sessions must be enabled to get client info
        throw new UnsupportedOperationException();
    }

    default List<Root> roots(Duration timeout)
    {
        // sessions must be enabled to get roots
        throw new UnsupportedOperationException();
    }
}
