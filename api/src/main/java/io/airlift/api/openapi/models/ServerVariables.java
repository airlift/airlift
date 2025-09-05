package io.airlift.api.openapi.models;

import com.fasterxml.jackson.annotation.JsonValue;

import java.util.HashMap;
import java.util.Map;

@SuppressWarnings({"checkstyle:MethodName", "checkstyle:ParameterName", "checkstyle:MemberName"})
public class ServerVariables
{
    private final Map<String, ServerVariable> serverVariables = new HashMap<>();

    @JsonValue
    public Map<String, ServerVariable> getServerVariables()
    {
        return serverVariables;
    }

    public ServerVariables addServerVariable(String name, ServerVariable item)
    {
        serverVariables.put(name, item);
        return this;
    }
}
