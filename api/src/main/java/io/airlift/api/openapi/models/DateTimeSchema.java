package io.airlift.api.openapi.models;

import java.time.OffsetDateTime;

@SuppressWarnings({"checkstyle:MethodName", "checkstyle:ParameterName", "checkstyle:MemberName"})
public class DateTimeSchema
        extends Schema<OffsetDateTime>
{
    public DateTimeSchema()
    {
        super("string", "date-time");
    }

    @Override
    public DateTimeSchema type(String type)
    {
        super.setType(type);
        return this;
    }

    @Override
    public DateTimeSchema format(String format)
    {
        super.setFormat(format);
        return this;
    }
}
