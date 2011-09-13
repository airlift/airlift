package com.proofpoint.event.client;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.io.NullOutputStream;
import com.proofpoint.event.client.NestedDummyEventClass.NestedPart;
import com.proofpoint.json.ObjectMapperProvider;
import org.codehaus.jackson.map.ObjectMapper;
import org.joda.time.DateTime;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Set;
import java.util.UUID;

import static com.proofpoint.event.client.ChainedCircularEventClass.ChainedPart;
import static com.proofpoint.event.client.EventTypeMetadata.getValidEventTypeMetaDataSet;
import static org.testng.Assert.assertEquals;

public class TestJsonEventWriter
{
    private JsonEventWriter eventWriter;

    @BeforeMethod
    public void setup()
            throws Exception
    {
        ObjectMapper objectMapper = new ObjectMapperProvider().get();
        Set<EventTypeMetadata<?>> eventTypes = getValidEventTypeMetaDataSet(
                FixedDummyEventClass.class, NestedDummyEventClass.class, CircularEventClass.class, ChainedCircularEventClass.class);
        eventWriter = new JsonEventWriter(objectMapper, eventTypes, new HttpEventClientConfig());
    }

    @Test
    public void testEventWriter()
            throws Exception
    {
        assertEventJson(createEventGenerator(TestingUtils.getEvents()), "events.json");
    }

    @Test
    public void testNullValue()
            throws Exception
    {
        FixedDummyEventClass event = new FixedDummyEventClass(
                "localhost", new DateTime("2011-09-09T01:59:59.999Z"), UUID.fromString("1ea8ca34-db36-11e0-b76f-8b7d505ab1ad"), 123, null);

        assertEventJson(createEventGenerator(ImmutableList.of(event)), "nullValue.json");
    }

    @Test
    public void testNestedEvent()
            throws Exception
    {
        NestedDummyEventClass nestedEvent = new NestedDummyEventClass(
                "localhost", new DateTime("2011-09-09T01:48:08.888Z"), UUID.fromString("6b598c2a-0a95-4f3f-9298-5a4d70ca13fc"), 9999, "nested",
                ImmutableList.of("abc", "xyz"),
                new NestedPart("first", new NestedPart("second", new NestedPart("third", null))),
                ImmutableList.of(new NestedPart("listFirst", new NestedPart("listSecond", null)), new NestedPart("listThird", null))
        );

        assertEventJson(createEventGenerator(ImmutableList.of(nestedEvent)), "nested.json");
    }

    @Test(expectedExceptions = InvalidEventException.class, expectedExceptionsMessageRegExp = "Cycle detected in event data:.*")
    public void testCircularEvent()
            throws Exception
    {
        eventWriter.writeEvents(createEventGenerator(ImmutableList.of(new CircularEventClass())), new NullOutputStream());
    }

    @Test(expectedExceptions = InvalidEventException.class, expectedExceptionsMessageRegExp = "Cycle detected in event data:.*")
    public void testChainedCircularEvent()
            throws Exception
    {
        ChainedPart a = new ChainedPart("a");
        ChainedPart b = new ChainedPart("b");
        ChainedPart c = new ChainedPart("c");
        a.setPart(b);
        b.setPart(c);
        c.setPart(a);

        ChainedCircularEventClass event = new ChainedCircularEventClass(a);

        eventWriter.writeEvents(createEventGenerator(ImmutableList.of(event)), new NullOutputStream());
    }

    private void assertEventJson(EventClient.EventGenerator<?> events, String resource)
            throws Exception
    {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        eventWriter.writeEvents(events, out);

        String json = out.toString(Charsets.UTF_8.name());
        assertEquals(json, TestingUtils.getNormalizedJson(resource));
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
