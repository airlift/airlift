package io.airlift.mcp.handler;

import java.util.Optional;

public interface MessageWriter
{
    void writeMessage(String data);

    void writeMessage(String data, Optional<String> messageId);

    void flushMessages();
}
