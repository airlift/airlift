package com.proofpoint.event.client;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Resources;
import com.proofpoint.json.ObjectMapperProvider;
import org.codehaus.jackson.map.ObjectMapper;
import org.joda.time.DateTime;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static com.proofpoint.event.client.EventTypeMetadata.getValidEventTypeMetaDataSet;
import static org.testng.Assert.assertEquals;

public class TestJsonEventWriter
{
    private final FixedDummyEventClass event1 = new FixedDummyEventClass(
            "localhost", new DateTime("2011-09-09T01:35:28.333Z"), UUID.fromString("8e248a16-da86-11e0-9e77-9fc96e21a396"), 5678, "foo");
    private final FixedDummyEventClass event2 = new FixedDummyEventClass(
            "localhost", new DateTime("2011-09-09T01:43:18.123Z"), UUID.fromString("94ac328a-da86-11e0-afe9-d30a5b7c4f68"), 1, "bar");
    private final FixedDummyEventClass event3 = new FixedDummyEventClass(
            "localhost", new DateTime("2011-09-09T01:45:55.555Z"), UUID.fromString("a30671a6-da86-11e0-bc43-971987242263"), 1234, "hello");

    private JsonEventWriter eventWriter;

    @BeforeMethod
    public void setup()
            throws Exception
    {
        ObjectMapper objectMapper = new ObjectMapperProvider().get();
        Set<EventTypeMetadata<?>> eventTypes = getValidEventTypeMetaDataSet(FixedDummyEventClass.class);
        eventWriter = new JsonEventWriter(objectMapper, eventTypes, new HttpEventClientConfig());
    }

    @Test
    public void testEventWriter()
            throws Exception
    {
        List<FixedDummyEventClass> events = ImmutableList.of(event1, event2, event3);

        assertEventJson(createEventGenerator(events), "events.json");
    }

    @Test
    public void testNullValue()
            throws Exception
    {
        FixedDummyEventClass event = new FixedDummyEventClass(
                "localhost", new DateTime("2011-09-09T01:59:59.999Z"), UUID.fromString("1ea8ca34-db36-11e0-b76f-8b7d505ab1ad"), 123, null);

        assertEventJson(createEventGenerator(ImmutableList.of(event)), "nullValue.json");
    }

    private void assertEventJson(EventClient.EventGenerator<FixedDummyEventClass> events, String resource)
            throws Exception
    {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        eventWriter.writeEvents(events, out);

        String json = out.toString(Charsets.UTF_8.name());
        assertEquals(json, getNormalizedJson(resource));
    }

    private static String getNormalizedJson(String resource)
            throws IOException
    {
        String json = Resources.toString(Resources.getResource(resource), Charsets.UTF_8);
        ObjectMapper mapper = new ObjectMapper();
        return mapper.writeValueAsString(mapper.readValue(json, Object.class));
    }

    private static <T> EventClient.EventGenerator<T> createEventGenerator(final Iterable<T> events)
    {
        return new EventClient.EventGenerator<T>()
        {
            @Override
            public void generate(EventClient.EventPoster<T> eventPoster)
                    throws IOException
            {
                for (T event : events) {
                    eventPoster.post(event);
                }
            }
        };
    }
}
