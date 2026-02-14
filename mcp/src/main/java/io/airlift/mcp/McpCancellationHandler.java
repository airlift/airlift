package io.airlift.mcp;

import io.airlift.log.Logger;

import java.util.Optional;

public interface McpCancellationHandler
{
    Logger log = Logger.get(McpCancellationHandler.class);

    McpCancellationHandler DEFAULT = (thread, id, reason) -> {
        log.info("Cancelling request or task %s. Reason: %s".formatted(id, reason.orElse("No reason provided")));
        thread.interrupt();
    };

    void cancel(Thread thread, Object id, Optional<String> reason);
}
