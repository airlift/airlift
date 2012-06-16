package com.proofpoint.event.client.privateclass;

import com.google.common.io.NullOutputStream;
import com.proofpoint.event.client.EventField;
import com.proofpoint.event.client.EventType;
import com.proofpoint.event.client.JsonEventSerializer;
import org.codehaus.jackson.JsonFactory;
import org.codehaus.jackson.JsonGenerator;
import org.testng.annotations.Test;

import java.io.IOException;

public class TestSerializingPrivateEvent
{
    @Test
    public void testSerialize()
            throws IOException
    {
        JsonEventSerializer serializer = new JsonEventSerializer(PrivateEvent.class);
        JsonGenerator generator = new JsonFactory().createJsonGenerator(new NullOutputStream());
        serializer.serialize(new PrivateEvent(), generator);
    }

    @EventType("Private")
    private static class PrivateEvent
    {
        @EventField
        public String getName()
        {
            return "private";
        }
    }
}
