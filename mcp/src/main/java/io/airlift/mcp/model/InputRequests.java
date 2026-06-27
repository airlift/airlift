package io.airlift.mcp.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableMap;

import java.util.Map;
import java.util.Optional;

public sealed interface InputRequests
        permits CallToolResult,
                GetPromptResult,
                ReadResourceResult
{
    @JsonProperty
    Optional<ResultType> resultType();

    @JsonProperty
    Optional<String> requestState();

    @JsonProperty
    Optional<Map<String, InputRequest>> inputRequests();

    Object withInputRequests(Optional<ResultType> resultType, Optional<String> requestState, Optional<Map<String, InputRequest>> inputRequests);

    abstract class Builder<T extends InputRequests>
    {
        protected final ImmutableMap.Builder<String, InputRequest> inputRequests = ImmutableMap.builder();
        protected Optional<String> requestState = Optional.empty();

        Builder() {}

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

        public abstract T build();
    }
}
