package io.airlift.mcp.handler;

public interface MessageWriter
{
    void writeMessage(String data);

    void flushMessages();
}
