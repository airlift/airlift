package com.proofpoint.event.client;

import com.google.common.collect.ImmutableList;
import com.proofpoint.event.client.EventClient.EventGenerator;
import com.proofpoint.event.client.EventClient.EventPoster;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.IOException;

public abstract class AbstractTestInMemoryEventClient
{
    private final DummyEventClass event1 = new DummyEventClass(1.234, 5678, "foo", true);
    private final DummyEventClass event2 = new DummyEventClass(0.001, 1, "bar", false);
    private final DummyEventClass event3 = new DummyEventClass(0.001, 5678, "foo", false);
    protected InMemoryEventClient eventClient;

    @Test
    public void testPostSingleEvent()
    {
        eventClient.post(event1);

        Assert.assertEquals(eventClient.getEvents(), ImmutableList.of(event1));
    }

    @Test
    public void testPostMultiple()
    {
        eventClient.post(event1);
        eventClient.post(event2);
        eventClient.post(event3);

        Assert.assertEquals(eventClient.getEvents(),
                ImmutableList.of(event1, event2, event3));
    }

    @Test
    public void testPostVarArgs()
    {
        eventClient.post(event1, event2, event3);

        Assert.assertEquals(eventClient.getEvents(),
                ImmutableList.of(event1, event2, event3));
    }

    @Test
    public void testPostIterable()
    {
        eventClient.post(ImmutableList.of(event1, event2, event3));

        Assert.assertEquals(eventClient.getEvents(),
                ImmutableList.of(event1, event2, event3));
    }

    @Test
    public void testPostEventPoster()
    {
        eventClient.post(new EventGenerator<Object>()
        {
            @Override
            public void generate(EventPoster<Object> objectEventPoster)
                    throws IOException
            {
                objectEventPoster.post(event1);
                objectEventPoster.post(event2);
                objectEventPoster.post(event3);
            }
        });

        Assert.assertEquals(eventClient.getEvents(),
                ImmutableList.of(event1, event2, event3));
    }
}
