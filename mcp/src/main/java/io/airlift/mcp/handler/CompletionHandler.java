package io.airlift.mcp.handler;

import io.airlift.mcp.McpNotifier;
import io.airlift.mcp.model.Completion;
import io.airlift.mcp.model.CompletionRequest;

import java.util.Optional;

public interface CompletionHandler
{
    Optional<Completion> completeCompletion(RequestContext requestContext, McpNotifier notifier, CompletionRequest completionRequest);
}
