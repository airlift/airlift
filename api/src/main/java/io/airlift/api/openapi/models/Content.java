package io.airlift.api.openapi.models;

import com.fasterxml.jackson.annotation.JsonValue;

import java.util.LinkedHashMap;
import java.util.Map;

@SuppressWarnings({"checkstyle:MethodName", "checkstyle:ParameterName", "checkstyle:MemberName"})
public class Content
{
    private final Map<String, MediaType> mediaTypes = new LinkedHashMap<>();

    public Content addMediaType(String name, MediaType item)
    {
        mediaTypes.put(name, item);
        return this;
    }

    @JsonValue
    public Map<String, MediaType> getMediaTypes()
    {
        return mediaTypes;
    }
}
