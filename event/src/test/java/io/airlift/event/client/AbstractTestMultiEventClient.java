/*
 * Copyright 2010 Proofpoint, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.airlift.event.client;

import com.google.common.collect.ImmutableList;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;

@Test(singleThreaded = true)
public abstract class AbstractTestMultiEventClient
{
    private final DummyEventClass event1 = new DummyEventClass(1.234, 5678, "foo", true);
    private final DummyEventClass event2 = new DummyEventClass(0.001, 1, "bar", false);
    private final DummyEventClass event3 = new DummyEventClass(0.001, 5678, "foo", false);

    protected InMemoryEventClient memoryEventClient1;
    protected InMemoryEventClient memoryEventClient2;
    protected EventClient eventClient;

    @Test
    public void testPostSingleEvent()
    {
        eventClient.post(event1);

        assertEquals(memoryEventClient1.getEvents(), ImmutableList.of(event1));
        assertEquals(memoryEventClient2.getEvents(), ImmutableList.of(event1));
    }

    @Test
    public void testPostMultiple()
    {
        eventClient.post(event1);
        eventClient.post(event2);
        eventClient.post(event3);

        assertEquals(memoryEventClient1.getEvents(),
                ImmutableList.of(event1, event2, event3));
        assertEquals(memoryEventClient2.getEvents(),
                ImmutableList.of(event1, event2, event3));
    }

    @Test
    public void testPostVarArgs()
    {
        eventClient.post(event1, event2, event3);

        assertEquals(memoryEventClient1.getEvents(),
                ImmutableList.of(event1, event2, event3));
        assertEquals(memoryEventClient2.getEvents(),
                ImmutableList.of(event1, event2, event3));
    }

    @Test
    public void testPostIterable()
    {
        eventClient.post(ImmutableList.of(event1, event2, event3));

        assertEquals(memoryEventClient1.getEvents(),
                ImmutableList.of(event1, event2, event3));
        assertEquals(memoryEventClient2.getEvents(),
                ImmutableList.of(event1, event2, event3));
    }

    @Test
    public void testPostEventPoster()
    {
        eventClient.post(objectEventPoster -> {
            objectEventPoster.post(event1);
            objectEventPoster.post(event2);
            objectEventPoster.post(event3);
        });

        assertEquals(memoryEventClient1.getEvents(),
                ImmutableList.of(event1, event2, event3));
        assertEquals(memoryEventClient2.getEvents(),
                ImmutableList.of(event1, event2, event3));
    }
}
