package io.airlift.mcp;

import io.airlift.log.Logger;

import java.util.Optional;

public interface McpCancellationHandler
{
    Logger log = Logger.get(McpCancellationHandler.class);

    McpCancellationHandler DEFAULT = (thread, requestId, reason) -> {
        log.info("Cancelling request %s. Reason: %s".formatted(requestId, reason.orElse("No reason provided")));
        thread.interrupt();
    };

    void cancelRequest(Thread thread, Object requestId, Optional<String> reason);
}
