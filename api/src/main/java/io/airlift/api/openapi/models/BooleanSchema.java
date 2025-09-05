package io.airlift.api.openapi.models;

@SuppressWarnings({"checkstyle:MethodName", "checkstyle:ParameterName", "checkstyle:MemberName"})
public class BooleanSchema
        extends Schema<Boolean>
{
    public BooleanSchema()
    {
        super("boolean", null);
    }

    @Override
    public BooleanSchema type(String type)
    {
        super.setType(type);
        return this;
    }
}
