package io.airlift.json.uuid;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.jsonFormatVisitors.JsonFormatVisitorWrapper;
import com.fasterxml.jackson.databind.ser.std.StdScalarSerializer;

import java.io.IOException;
import java.util.UUID;

public class CustomUuidSerializer
        extends StdScalarSerializer<UUID>
{
    public CustomUuidSerializer()
    {
        super(UUID.class);
    }

    @Override
    public void serialize(UUID value, JsonGenerator jgen, SerializerProvider provider)
            throws IOException
    {
        jgen.writeString(UUIDs.toString(value));
    }

    @Override
    public void acceptJsonFormatVisitor(JsonFormatVisitorWrapper visitor, JavaType typeHint)
    {
        visitor.expectStringFormat(typeHint);
    }
}
