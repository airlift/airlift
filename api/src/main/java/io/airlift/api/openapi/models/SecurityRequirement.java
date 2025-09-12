package io.airlift.api.openapi.models;

import com.fasterxml.jackson.annotation.JsonValue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SuppressWarnings({"checkstyle:MethodName", "checkstyle:ParameterName", "checkstyle:MemberName"})
public class SecurityRequirement
{
    private final Map<String, List<String>> map = new HashMap<>();

    @JsonValue
    public Map<String, List<String>> getMap()
    {
        return map;
    }

    public SecurityRequirement addList(String name, String item)
    {
        map.put(name, Collections.singletonList(item));
        return this;
    }

    public SecurityRequirement addList(String name, List<String> item)
    {
        map.put(name, item);
        return this;
    }

    public SecurityRequirement addList(String name)
    {
        map.put(name, new ArrayList<>());
        return this;
    }
}
