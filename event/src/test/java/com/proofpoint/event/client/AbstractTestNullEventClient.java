package com.proofpoint.event.client;

import com.google.common.collect.ImmutableList;
import com.proofpoint.event.client.EventClient.EventGenerator;
import com.proofpoint.event.client.EventClient.EventPoster;
import org.testng.annotations.Test;

import java.io.IOException;

public abstract class AbstractTestNullEventClient
{
    protected NullEventClient eventClient;

    @Test
    public void testPostEvents()
    {
        eventClient.post(new Object());
        eventClient.post(ImmutableList.of(new Object()));
        eventClient.post(new EventGenerator<Object>()
        {
            public void generate(EventPoster<Object> objectEventPoster)
                    throws IOException
            {
                objectEventPoster.post(new Object());
            }
        });
    }
}
