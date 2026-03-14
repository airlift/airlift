package io.airlift.jsonrpc.server;

import io.airlift.jsonrpc.model.JsonRpcErrorDetail;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.airlift.jsonrpc.model.JsonRpcErrorCode.CONNECTION_CLOSED;
import static java.util.Objects.requireNonNull;

public class SseMessageWriter
{
    private final HttpServletResponse response;
    private final AtomicBoolean hasBeenUpgraded = new AtomicBoolean();

    public SseMessageWriter(HttpServletResponse response)
    {
        this.response = requireNonNull(response, "response is null");
    }

    public void write(String data)
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
            throw new JsonRpcException(e, new JsonRpcErrorDetail(CONNECTION_CLOSED, "Connection appears to be closed while writing message"));
        }
    }

    public void writeMessage(String data)
    {
        String messageId = UUID.randomUUID().toString();

        internalWriteMessage(messageId, data);
    }

    void internalWriteMessage(String messageId, String data)
    {
        if (hasBeenUpgraded.compareAndSet(false, true)) {
            response.setContentType("text/event-stream");
        }

        try {
            PrintWriter writer = response.getWriter();
            writer.write("id: " + encode(messageId) + "\n");
            writer.write("data: " + encode(data) + "\n\n");
        }
        catch (IOException e) {
            throw new JsonRpcException(e, new JsonRpcErrorDetail(CONNECTION_CLOSED, "Connection appears to be closed while writing message"));
        }
    }

    public void flushMessages()
    {
        try {
            response.getWriter().flush();
        }
        catch (IOException e) {
            throw new JsonRpcException(e, new JsonRpcErrorDetail(CONNECTION_CLOSED, "Connection appears to be closed while writing message"));
        }
    }

    public boolean hasBeenUpgraded()
    {
        return hasBeenUpgraded.get();
    }

    private static String encode(String str)
    {
        // Escape newlines and carriage returns for SSE compliance
        return str.replace("\n", "\\n").replace("\r", "\\r");
    }
}
