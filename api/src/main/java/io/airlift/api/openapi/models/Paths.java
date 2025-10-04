package io.airlift.api.openapi.models;

import com.fasterxml.jackson.annotation.JsonValue;

import java.util.HashMap;
import java.util.Map;

@SuppressWarnings({"checkstyle:MethodName", "checkstyle:ParameterName", "checkstyle:MemberName"})
public class Paths
{
    private final Map<String, PathItem> paths = new HashMap<>();

    @JsonValue
    public Map<String, PathItem> getPaths()
    {
        return paths;
    }

    public Paths addPathItem(String name, PathItem item)
    {
        paths.put(name, item);
        return this;
    }

    public PathItem get(String path)
    {
        return paths.get(path);
    }

    public PathItem put(String path, PathItem item)
    {
        return paths.put(path, item);
    }
}
