package io.airlift.json.uuid;

import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.JdkDeserializers.UUIDDeserializer;

import java.io.IOException;
import java.util.UUID;

public class CustomUuidDeserializer
        extends UUIDDeserializer
{
    @Override
    protected UUID _deserialize(String value, DeserializationContext ctxt)
            throws IOException
    {
        return UUIDs.fromString(value);
    }
}
