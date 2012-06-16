package io.airlift.event.client;

import com.google.common.collect.ImmutableList;
import io.airlift.event.client.EventClient.EventGenerator;
import io.airlift.event.client.EventClient.EventPoster;
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
