package io.airlift.mcp.reference;

import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static io.airlift.mcp.McpException.exception;
import static java.util.Objects.requireNonNull;

class SsePrintWriter
        extends PrintWriter
{
    private final AtomicLong nextId = new AtomicLong();
    private final Runnable upgradeHandler;
    private boolean hasBeenUpgraded;

    SsePrintWriter(OutputStream out, Runnable upgradeHandler)
    {
        super(out, false);

        this.upgradeHandler = requireNonNull(upgradeHandler, "upgradeHandler is null");
    }

    @Override
    public void write(String data)
    {
        if (hasBeenUpgraded) {
            writeMessage(data);
        }
        else {
            // no messages were sent, keep it as a standard JSON response
            super.write(data);
        }
    }

    public void writeMessage(String data)
    {
        writeMessage(data, Optional.of(Long.toString(nextId.getAndIncrement())));
    }

    public void writeMessage(String data, Optional<String> messageId)
    {
        if (!hasBeenUpgraded) {
            hasBeenUpgraded = true;
            // this will change response to an SSE response
            upgradeHandler.run();
        }

        messageId.ifPresent(id -> super.write("id: " + encode(id) + "\n"));

        super.write("data: " + encode(data) + "\n\n");
        if (checkError()) {
            throw exception("Error writing SSE message");
        }
    }

    private String encode(String str)
    {
        // Escape newlines and carriage returns for SSE compliance
        return str.replace("\n", "\\n").replace("\r", "\\r");
    }
}
