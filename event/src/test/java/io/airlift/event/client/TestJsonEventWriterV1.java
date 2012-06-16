package io.airlift.event.client;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import org.joda.time.DateTime;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Set;
import java.util.UUID;

import static io.airlift.event.client.EventTypeMetadata.getValidEventTypeMetaDataSet;
import static org.testng.Assert.assertEquals;

public class TestJsonEventWriterV1
{
    private JsonEventWriter eventWriter;

    @BeforeMethod
    public void setup()
            throws Exception
    {
        Set<EventTypeMetadata<?>> eventTypes = getValidEventTypeMetaDataSet(FixedDummyEventClass.class);
        eventWriter = new JsonEventWriter(eventTypes, new HttpEventClientConfig().setJsonVersion(1));
    }

    @Test
    public void testEventWriter()
            throws Exception
    {
        assertEventJson(createEventGenerator(TestingUtils.getEvents()), "V1events.json");
    }

    @Test
    public void testNullValue()
            throws Exception
    {
        FixedDummyEventClass event = new FixedDummyEventClass(
                "localhost", new DateTime("2011-09-09T01:59:59.999Z"), UUID.fromString("1ea8ca34-db36-11e0-b76f-8b7d505ab1ad"), 123, null);

        assertEventJson(createEventGenerator(ImmutableList.of(event)), "V1event.json");
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
