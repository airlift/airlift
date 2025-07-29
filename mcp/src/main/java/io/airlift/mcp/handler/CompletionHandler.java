package io.airlift.mcp.handler;

import io.airlift.mcp.McpNotifier;
import io.airlift.mcp.model.Completion;
import io.airlift.mcp.model.CompletionRequest;
import io.airlift.mcp.session.SessionId;
import jakarta.ws.rs.core.Request;

import java.util.Optional;

public interface CompletionHandler
{
    Optional<Completion> completeCompletion(Request request, SessionId sessionId, McpNotifier notifier, CompletionRequest completionRequest);
}
