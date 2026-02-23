package io.airlift.api.openapi.models;

import com.fasterxml.jackson.annotation.JsonValue;

import java.util.LinkedHashMap;
import java.util.Map;

@SuppressWarnings({"checkstyle:MethodName", "checkstyle:ParameterName", "checkstyle:MemberName"})
public class Scopes
{
    private final Map<String, String> scopes = new LinkedHashMap<>();

    @JsonValue
    public Map<String, String> getScopes()
    {
        return scopes;
    }

    public Scopes addString(String name, String item)
    {
        scopes.put(name, item);
        return this;
    }
}
