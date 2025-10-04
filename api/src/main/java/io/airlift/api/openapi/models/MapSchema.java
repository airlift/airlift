package io.airlift.api.openapi.models;

@SuppressWarnings({"checkstyle:MethodName", "checkstyle:ParameterName", "checkstyle:MemberName"})
public class MapSchema
        extends Schema<Object>
{
    public MapSchema()
    {
        super("object", null);
    }

    @Override
    public MapSchema type(String type)
    {
        super.setType(type);
        return this;
    }

    @Override
    public MapSchema additionalProperties(Object additionalProperties)
    {
        super.additionalProperties(additionalProperties);
        return this;
    }
}
