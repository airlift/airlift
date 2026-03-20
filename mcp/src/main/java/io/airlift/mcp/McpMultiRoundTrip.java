package io.airlift.mcp;

import io.airlift.mcp.model.CallToolRequest;
import io.airlift.mcp.model.IncompleteToolResult;
import io.airlift.mcp.model.InputRequest;
import io.airlift.mcp.model.InputResponse;

import java.util.Collection;
import java.util.Optional;

public interface McpMultiRoundTrip
{
    interface InputRequestBuilder
    {
        InputRequestBuilder addInputRequest(String key, InputRequest inputRequest);

        InputRequestBuilder withRequestState(String requestState);

        <T> InputRequestBuilder withRequestStateObject(Class<T> type, T requestState);

        IncompleteToolResult build();
    }

    interface ParsedInputResponse
    {
        boolean hasInputResponses();

        Optional<String> requestState();

        <T> Optional<T> requestStateObject(Class<T> requestStateType);

        Collection<String> inputResponseKeys();

        <R extends InputResponse> Optional<R> inputResponse(String key, Class<R> type);
    }

    InputRequestBuilder inputRequestBuilder();

    ParsedInputResponse inputResponseParser(CallToolRequest callToolRequest);
}
