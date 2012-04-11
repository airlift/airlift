package com.proofpoint.event.client;

import com.google.common.base.Charsets;
import org.codehaus.jackson.JsonEncoding;
import org.codehaus.jackson.JsonFactory;
import org.codehaus.jackson.JsonGenerator;
import org.testng.annotations.Test;

import java.io.ByteArrayOutputStream;

import static org.testng.Assert.assertEquals;

public class TestJsonEventSerializer
{
    @Test
    public void testEventSerializer()
            throws Exception
    {
        JsonEventSerializer eventSerializer = new JsonEventSerializer(FixedDummyEventClass.class);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        JsonGenerator jsonGenerator = new JsonFactory().createJsonGenerator(out, JsonEncoding.UTF8);

        FixedDummyEventClass event = TestingUtils.getEvents().get(0);
        eventSerializer.serialize(event, jsonGenerator);

        String json = out.toString(Charsets.UTF_8.name());
        assertEquals(json, TestingUtils.getNormalizedJson("event.json"));
    }
}
