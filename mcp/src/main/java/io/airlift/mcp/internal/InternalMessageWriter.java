package io.airlift.mcp.internal;

import io.airlift.mcp.handler.MessageWriter;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static java.util.Objects.requireNonNull;

class InternalMessageWriter
        implements MessageWriter
{
    private final HttpServletResponse response;
    private final AtomicLong nextId = new AtomicLong();
    private final AtomicBoolean hasBeenUpgraded = new AtomicBoolean();

    InternalMessageWriter(HttpServletResponse response)
    {
        this.response = requireNonNull(response, "response is null");
    }

    void write(String data)
    {
        try {
            PrintWriter writer = response.getWriter();
            if (hasBeenUpgraded.get()) {
                writeMessage(data);
            }
            else {
                // no messages were sent, keep it as a standard JSON response
                writer.write(data);
            }
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void writeMessage(String data)
    {
        writeMessage(data, Optional.of(Long.toString(nextId.getAndIncrement())));
    }

    @Override
    public void writeMessage(String data, Optional<String> messageId)
    {
        if (hasBeenUpgraded.compareAndSet(false, true)) {
            response.setContentType("text/event-stream");
        }

        try {
            PrintWriter writer = response.getWriter();
            messageId.ifPresent(id -> writer.write("id: " + encode(id) + "\n"));
            writer.write("data: " + encode(data) + "\n\n");
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void flushMessages()
    {
        try {
            response.getWriter().flush();
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String encode(String str)
    {
        // Escape newlines and carriage returns for SSE compliance
        return str.replace("\n", "\\n").replace("\r", "\\r");
    }
}
