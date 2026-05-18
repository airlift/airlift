package io.airlift.mcp.messages;

import jakarta.servlet.http.HttpServletResponse;

public interface MessageWriter
{
    static MessageWriter newMessageWriter(HttpServletResponse response)
    {
        return new MessageWriterImpl(response, false);
    }

    static ResumableMessageWriter newResumableMessageWriter(HttpServletResponse response)
    {
        return new MessageWriterImpl(response, true);
    }

    /**
     * If this stream has been upgraded, write the data
     * as an SSE message. Otherwise, output as a standard
     * HTTP response.
     */
    void write(String data);

    /**
     * Upgrade to SSE if it hasn't occurred already and
     * output an SSE message.
     */
    void writeMessage(String data);

    /**
     * Flush any pending data
     */
    void flush();

    /**
     * Return {@code true} if this stream has been upgraded to SSE
     */
    boolean hasBeenUpgraded();
}
