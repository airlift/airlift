package io.airlift.mcp.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableMap;

import java.util.Map;
import java.util.Optional;

import static com.google.common.base.Preconditions.checkState;
import static java.util.Objects.requireNonNull;

public sealed interface InputRequests
        permits CallToolResult,
                GetPromptResult,
                ReadResourceResult
{
    @JsonProperty
    Optional<String> requestState();

    @JsonProperty
    Optional<Map<String, InputRequest>> inputRequests();

    interface Factory<T extends InputRequests>
    {
        T build(Optional<String> requestState, Map<String, InputRequest> inputRequests);
    }

    static <T extends InputRequests> Builder<T> builder(Factory<T> factory)
    {
        return new Builder<>(factory);
    }

    class Builder<T extends InputRequests>
    {
        private final Factory<T> factory;
        private final ImmutableMap.Builder<String, InputRequest> inputRequests = ImmutableMap.builder();
        private Optional<String> requestState = Optional.empty();

        private Builder(Factory<T> factory)
        {
            this.factory = requireNonNull(factory, "factory is null");
        }

        public Builder<T> withInputResponses(InputResponses<?> inputResponses)
        {
            this.requestState = inputResponses.requestState();
            return this;
        }

        public Builder<T> withRequestState(String requestState)
        {
            this.requestState = Optional.of(requestState);
            return this;
        }

        public Builder<T> add(String key, String method, Object params)
        {
            inputRequests.put(key, new InputRequest(method, params));
            return this;
        }

        public T build()
        {
            Map<String, InputRequest> inputRequestsMap = inputRequests.buildOrThrow();
            checkState(!inputRequestsMap.isEmpty(), "No requests were added");
            return factory.build(requestState, inputRequestsMap);
        }
    }
}
