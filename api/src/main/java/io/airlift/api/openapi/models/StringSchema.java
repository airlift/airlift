package io.airlift.api.openapi.models;

@SuppressWarnings({"checkstyle:MethodName", "checkstyle:ParameterName", "checkstyle:MemberName"})
public class StringSchema
        extends Schema<String>
{
    public StringSchema()
    {
        super("string", null);
    }

    @Override
    public StringSchema type(String type)
    {
        super.setType(type);
        return this;
    }

    public StringSchema addEnumItem(String _enumItem)
    {
        super.addEnumItemObject(_enumItem);
        return this;
    }
}
