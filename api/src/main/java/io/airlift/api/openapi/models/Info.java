package io.airlift.api.openapi.models;

import com.fasterxml.jackson.annotation.JsonProperty;

@SuppressWarnings({"checkstyle:MethodName", "checkstyle:ParameterName", "checkstyle:MemberName"})
public class Info
{
    private String title;
    private String description;
    private String version;
    private String summary;

    @JsonProperty
    public String getTitle()
    {
        return title;
    }

    public void setTitle(String title)
    {
        this.title = title;
    }

    public Info title(String title)
    {
        this.title = title;
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

    public Info description(String description)
    {
        this.description = description;
        return this;
    }

    @JsonProperty
    public String getVersion()
    {
        return version;
    }

    public void setVersion(String version)
    {
        this.version = version;
    }

    public Info version(String version)
    {
        this.version = version;
        return this;
    }

    @JsonProperty
    public String getSummary()
    {
        return summary;
    }

    public void setSummary(String summary)
    {
        this.summary = summary;
    }

    public Info summary(String summary)
    {
        this.summary = summary;
        return this;
    }
}
