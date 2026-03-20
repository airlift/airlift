package io.airlift.mcp.internal;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import io.airlift.mcp.McpMultiRoundTrip;
import io.airlift.mcp.McpMultiRoundTripEncoder;
import io.airlift.mcp.model.CallToolRequest;
import io.airlift.mcp.model.IncompleteToolResult;
import io.airlift.mcp.model.InputRequest;
import io.airlift.mcp.model.InputResponse;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static java.util.Objects.requireNonNull;

class InternalMultiRoundTrip
        implements McpMultiRoundTrip
{
    private final McpMultiRoundTripEncoder encoder;
    private final JsonMapper jsonMapper;

    @Inject
    InternalMultiRoundTrip(McpMultiRoundTripEncoder encoder, JsonMapper jsonMapper)
    {
        this.encoder = requireNonNull(encoder, "encoder is null");
        this.jsonMapper = requireNonNull(jsonMapper, "jsonMapper is null");
    }

    @Override
    public InputRequestBuilder inputRequestBuilder()
    {
        return new InputRequestBuilder()
        {
            private final Map<String, InputRequest> inputRequests = new HashMap<>();
            private Optional<String> requestState = Optional.empty();

            @Override
            public InputRequestBuilder addInputRequest(String key, InputRequest inputRequest)
            {
                checkArgument(!inputRequests.containsKey(key), "Duplicate input request key: %s", key);

                inputRequests.put(key, inputRequest);

                return this;
            }

            @Override
            public InputRequestBuilder withRequestState(String requestState)
            {
                validateEmptyRequestState();

                this.requestState = Optional.of(requestState);

                return this;
            }

            @Override
            public <T> InputRequestBuilder withRequestStateObject(Class<T> type, T requestState)
            {
                validateEmptyRequestState();

                this.requestState = Optional.of(encoder.encode(type, requestState));

                return this;
            }

            @Override
            public IncompleteToolResult build()
            {
                checkState(!inputRequests.isEmpty(), "At least one input request is required");

                return new IncompleteToolResult("incomplete", Optional.of(inputRequests), requestState);
            }

            private void validateEmptyRequestState()
            {
                checkState(requestState.isEmpty(), "Request state is already set");
            }
        };
    }

    @Override
    public ParsedInputResponse inputResponseParser(CallToolRequest callToolRequest)
    {
        return new ParsedInputResponse()
        {
            @Override
            public boolean hasInputResponses()
            {
                return callToolRequest.inputResponses().isPresent();
            }

            @Override
            public Optional<String> requestState()
            {
                return callToolRequest.requestState();
            }

            @Override
            public <T> Optional<T> requestStateObject(Class<T> requestStateType)
            {
                return requestState().map(state -> encoder.decode(requestStateType, state));
            }

            @Override
            public Collection<String> inputResponseKeys()
            {
                return callToolRequest.inputResponses()
                        .map(Map::keySet)
                        .orElseGet(ImmutableSet::of);
            }

            @Override
            public <R extends InputResponse> Optional<R> inputResponse(String key, Class<R> type)
            {
                return callToolRequest.inputResponses()
                        .flatMap(inputResponses -> Optional.ofNullable(inputResponses.get(key)))
                        .map(response -> {
                            try {
                                return jsonMapper.convertValue(response, type);
                            }
                            catch (IllegalArgumentException e) {
                                throw new RuntimeException(e);
                            }
                        });
            }
        };
    }
}
