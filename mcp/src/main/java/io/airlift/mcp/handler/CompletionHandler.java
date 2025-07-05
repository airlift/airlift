package io.airlift.mcp.handler;

import io.airlift.mcp.McpNotifier;
import io.airlift.mcp.model.Completion;
import io.airlift.mcp.model.CompletionRequest;
import jakarta.ws.rs.core.Request;

import java.util.Optional;

public interface CompletionHandler
{
    Optional<Completion> completeCompletion(Request request, McpNotifier notifier, CompletionRequest completionRequest);
}
