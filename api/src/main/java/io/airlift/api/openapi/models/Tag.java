package io.airlift.api.openapi.models;

import com.fasterxml.jackson.annotation.JsonProperty;

@SuppressWarnings({"checkstyle:MethodName", "checkstyle:ParameterName", "checkstyle:MemberName"})
public class Tag
{
    private String name;
    private String description;

    @JsonProperty
    public String getName()
    {
        return name;
    }

    public void setName(String name)
    {
        this.name = name;
    }

    public Tag name(String name)
    {
        this.name = name;
        return this;
    }

    @JsonProperty
    public String getDescription()
    {
        return description;
    }

    public void setDescription(String description)
    {
        this.description = description;
    }

    public Tag description(String description)
    {
        this.description = description;
        return this;
    }
}
