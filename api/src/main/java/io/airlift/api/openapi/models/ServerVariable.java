package io.airlift.api.openapi.models;

import com.fasterxml.jackson.annotation.JsonProperty;

@SuppressWarnings({"checkstyle:MethodName", "checkstyle:ParameterName", "checkstyle:MemberName"})
public class ServerVariable
{
    private String _default;
    private String description;

    @JsonProperty
    public String getDefault()
    {
        return _default;
    }

    public void setDefault(String _default)
    {
        this._default = _default;
    }

    public ServerVariable _default(String _default)
    {
        this._default = _default;
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

    public ServerVariable description(String description)
    {
        this.description = description;
        return this;
    }
}
