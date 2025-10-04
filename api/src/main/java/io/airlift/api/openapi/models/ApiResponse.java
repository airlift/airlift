package io.airlift.api.openapi.models;

import com.fasterxml.jackson.annotation.JsonProperty;

@SuppressWarnings({"checkstyle:MethodName", "checkstyle:ParameterName", "checkstyle:MemberName"})
public class ApiResponse
{
    private String description;
    private Content content;
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

    public ApiResponse description(String description)
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

    public ApiResponse content(Content content)
    {
        this.content = content;
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
            $ref = "#/components/responses/" + $ref;
        }
        this.$ref = $ref;
    }

    public ApiResponse $ref(String $ref)
    {
        set$ref($ref);
        return this;
    }
}
