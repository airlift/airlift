package io.airlift.api.openapi.models;

@SuppressWarnings({"checkstyle:MethodName", "checkstyle:ParameterName", "checkstyle:MemberName"})
public class ArraySchema
        extends Schema<Object>
{
    public ArraySchema()
    {
        super("array", null);
    }

    @Override
    public ArraySchema type(String type)
    {
        super.setType(type);
        return this;
    }

    @Override
    public ArraySchema items(Schema items)
    {
        super.setItems(items);
        return this;
    }
}
