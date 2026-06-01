package io.airlift.api.openapi.models;

import com.fasterxml.jackson.annotation.JsonProperty;

@SuppressWarnings({"checkstyle:MethodName", "checkstyle:ParameterName", "checkstyle:MemberName"})
public class MediaType
{
    private Schema schema;
    private Schema itemSchema;
    private Schema airliftEventSchema;

    @JsonProperty
    public Schema getSchema()
    {
        return schema;
    }

    public void setSchema(Schema schema)
    {
        this.schema = schema;
    }

    public MediaType schema(Schema schema)
    {
        this.schema = schema;
        return this;
    }

    @JsonProperty
    public Schema getItemSchema()
    {
        return itemSchema;
    }

    public void setItemSchema(Schema itemSchema)
    {
        this.itemSchema = itemSchema;
    }

    public MediaType itemSchema(Schema itemSchema)
    {
        this.itemSchema = itemSchema;
        return this;
    }

    @JsonProperty("x-airlift-event-schema")
    public Schema getAirliftEventSchema()
    {
        return airliftEventSchema;
    }

    public void setAirliftEventSchema(Schema airliftEventSchema)
    {
        this.airliftEventSchema = airliftEventSchema;
    }

    public MediaType airliftEventSchema(Schema airliftEventSchema)
    {
        this.airliftEventSchema = airliftEventSchema;
        return this;
    }
}
