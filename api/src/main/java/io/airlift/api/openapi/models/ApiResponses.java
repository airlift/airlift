package io.airlift.api.openapi.models;

import com.fasterxml.jackson.annotation.JsonValue;

import java.util.HashMap;
import java.util.Map;

@SuppressWarnings({"checkstyle:MethodName", "checkstyle:ParameterName", "checkstyle:MemberName"})
public class ApiResponses
{
    private final Map<String, ApiResponse> responses = new HashMap<>();

    public ApiResponses addApiResponse(String name, ApiResponse item)
    {
        responses.put(name, item);
        return this;
    }

    @JsonValue
    public Map<String, ApiResponse> getResponse()
    {
        return responses;
    }
}
