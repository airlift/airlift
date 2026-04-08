package io.airlift.mcp.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

public sealed interface InputRequest
        permits CreateMessageRequest, ElicitRequestForm, ElicitRequestUrl, ListRootsRequest
{
    @JsonIgnore
    String methodName();

    @JsonIgnore
    Class<? extends InputResponse> responseType();
}
