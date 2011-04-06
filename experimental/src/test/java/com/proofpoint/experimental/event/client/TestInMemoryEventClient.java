package com.proofpoint.experimental.event.client;

import com.google.common.collect.ImmutableList;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class TestInMemoryEventClient
{
    private InMemoryEventClient eventClient;

    @BeforeMethod
    public void setup()
            throws Exception
    {
        eventClient = new InMemoryEventClient();
    }

    @Test
    public void testPostSingleEvent()
    {
        DummyEventClass event = new DummyEventClass(1.234, 5678, "foo", true);

        eventClient.post(event);

        Assert.assertEquals(eventClient.getEvents(), ImmutableList.of(event));
    }

    @Test
    public void testPostMultipleEvents()
    {
        DummyEventClass event1 = new DummyEventClass(1.234, 5678, "foo", true);
        DummyEventClass event2 = new DummyEventClass(0.001, 1, "bar", false);
        DummyEventClass event3 = new DummyEventClass(0.001, 5678, "foo", false);

        eventClient.post(event1, event2, event3);

        Assert.assertEquals(eventClient.getEvents(),
                ImmutableList.of(event1, event2, event3));
    }

}
