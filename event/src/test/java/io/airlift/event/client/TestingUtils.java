package com.proofpoint.event.client;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Resources;
import org.codehaus.jackson.map.ObjectMapper;
import org.joda.time.DateTime;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

class TestingUtils
{
    public static List<FixedDummyEventClass> getEvents()
    {
        return ImmutableList.of(
                new FixedDummyEventClass("localhost", new DateTime("2011-09-09T01:35:28.333Z"), UUID.fromString("8e248a16-da86-11e0-9e77-9fc96e21a396"), 5678, "foo"),
                new FixedDummyEventClass("localhost", new DateTime("2011-09-09T01:43:18.123Z"), UUID.fromString("94ac328a-da86-11e0-afe9-d30a5b7c4f68"), 1, "bar"),
                new FixedDummyEventClass("localhost", new DateTime("2011-09-09T01:45:55.555Z"), UUID.fromString("a30671a6-da86-11e0-bc43-971987242263"), 1234, "hello")
        );
    }

    public static String getNormalizedJson(String resource)
            throws IOException
    {
        String json = Resources.toString(Resources.getResource(resource), Charsets.UTF_8);
        ObjectMapper mapper = new ObjectMapper();
        return mapper.writeValueAsString(mapper.readValue(json, Object.class));
    }
}
