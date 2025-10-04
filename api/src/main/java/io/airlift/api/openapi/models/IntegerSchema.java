package io.airlift.api.openapi.models;

@SuppressWarnings({"checkstyle:MethodName", "checkstyle:ParameterName", "checkstyle:MemberName"})
public class IntegerSchema
        extends Schema<Number>
{
    public IntegerSchema()
    {
        super("integer", "int32");
    }

    @Override
    public IntegerSchema type(String type)
    {
        super.setType(type);
        return this;
    }

    @Override
    public IntegerSchema format(String format)
    {
        super.setFormat(format);
        return this;
    }
}
