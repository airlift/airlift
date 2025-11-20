package io.airlift.mcp.handler;

import io.airlift.mcp.McpRequestContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.util.Optional;

public interface RequestContextProvider
{
    interface MessageWriter
    {
        void writeMessage(String data);

        void writeMessage(String data, Optional<String> messageId);

        void flushMessages();
    }

    McpRequestContext get(HttpServletRequest request, HttpServletResponse response, MessageWriter messageWriter, Optional<Object> progressToken);
}
