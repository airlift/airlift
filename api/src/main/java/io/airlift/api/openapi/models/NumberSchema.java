package io.airlift.api.openapi.models;

import java.math.BigDecimal;

@SuppressWarnings({"checkstyle:MethodName", "checkstyle:ParameterName", "checkstyle:MemberName"})
public class NumberSchema
        extends Schema<BigDecimal>
{
    public NumberSchema()
    {
        super("number", null);
    }

    @Override
    public NumberSchema type(String type)
    {
        super.setType(type);
        return this;
    }
}
