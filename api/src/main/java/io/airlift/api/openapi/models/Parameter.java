package io.airlift.api.openapi.models;

import com.fasterxml.jackson.annotation.JsonProperty;

@SuppressWarnings({"checkstyle:MethodName", "checkstyle:ParameterName", "checkstyle:MemberName"})
public class Parameter
{
    private String in;
    private String name;
    private String description;
    private Boolean required;
    private Boolean explode;
    private Schema schema;

    @JsonProperty
    public String getIn()
    {
        return in;
    }

    public void setIn(String in)
    {
        this.in = in;
    }

    public Parameter in(String in)
    {
        setIn(in);
        return this;
    }

    @JsonProperty
    public Boolean getExplode()
    {
        return explode;
    }

    public void setExplode(Boolean explode)
    {
        this.explode = explode;
    }

    @JsonProperty
    public String getName()
    {
        return name;
    }

    public void setName(String name)
    {
        this.name = name;
    }

    public Parameter name(String name)
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

    public Parameter description(String description)
    {
        this.description = description;
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

    public Parameter required(Boolean required)
    {
        this.required = required;
        return this;
    }

    @JsonProperty
    public Schema getSchema()
    {
        return schema;
    }

    public Parameter schema(Schema schema)
    {
        this.schema = schema;
        return this;
    }
}
