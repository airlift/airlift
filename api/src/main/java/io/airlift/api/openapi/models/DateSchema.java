package io.airlift.api.openapi.models;

import java.util.Date;

@SuppressWarnings({"checkstyle:MethodName", "checkstyle:ParameterName", "checkstyle:MemberName"})
public class DateSchema
        extends Schema<Date>
{
    public DateSchema()
    {
        super("string", "date");
    }

    @Override
    public DateSchema type(String type)
    {
        super.setType(type);
        return this;
    }

    @Override
    public DateSchema format(String format)
    {
        super.setFormat(format);
        return this;
    }
}
