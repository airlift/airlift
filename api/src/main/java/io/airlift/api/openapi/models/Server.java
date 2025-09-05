package io.airlift.api.openapi.models;

import com.fasterxml.jackson.annotation.JsonProperty;

@SuppressWarnings({"checkstyle:MethodName", "checkstyle:ParameterName", "checkstyle:MemberName"})
public class Server
{
    private String url;
    private String description;
    private ServerVariables variables;

    @JsonProperty
    public String getUrl()
    {
        return url;
    }

    public void setUrl(String url)
    {
        this.url = url;
    }

    public Server url(String url)
    {
        this.url = url;
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

    public Server description(String description)
    {
        this.description = description;
        return this;
    }

    @JsonProperty
    public ServerVariables getVariables()
    {
        return variables;
    }

    public void setVariables(ServerVariables variables)
    {
        this.variables = variables;
    }

    public Server variables(ServerVariables variables)
    {
        this.variables = variables;
        return this;
    }
}
