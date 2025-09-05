package io.airlift.api.openapi.models;

import com.fasterxml.jackson.annotation.JsonProperty;

@SuppressWarnings({"checkstyle:MethodName", "checkstyle:ParameterName", "checkstyle:MemberName"})
public class RequestBody
{
    private String description;
    private Content content;
    private Boolean required;
    private String $ref;

    @JsonProperty
    public String getDescription()
    {
        return description;
    }

    public void setDescription(String description)
    {
        this.description = description;
    }

    public RequestBody description(String description)
    {
        this.description = description;
        return this;
    }

    @JsonProperty
    public Content getContent()
    {
        return content;
    }

    public void setContent(Content content)
    {
        this.content = content;
    }

    public RequestBody content(Content content)
    {
        this.content = content;
        return this;
    }

    @JsonProperty
    public Boolean getRequired()
    {
        return required;
    }

    public void setRequired(Boolean required)
    {
        this.required = required;
    }

    public RequestBody required(Boolean required)
    {
        this.required = required;
        return this;
    }

    @JsonProperty
    public String get$ref()
    {
        return $ref;
    }

    public void set$ref(String $ref)
    {
        if ($ref != null && ($ref.indexOf('.') == -1 && $ref.indexOf('/') == -1)) {
            $ref = "#/components/requestBodies/" + $ref;
        }
        this.$ref = $ref;
    }

    public RequestBody $ref(String $ref)
    {
        set$ref($ref);
        return this;
    }
}
