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
package com.proofpoint.event.client;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.proofpoint.event.client.NestedDummyEventClass.NestedPart;
import com.proofpoint.node.NodeInfo;
import org.joda.time.DateTime;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Set;
import java.util.UUID;

import static com.google.common.io.ByteStreams.nullOutputStream;
import static com.proofpoint.event.client.ChainedCircularEventClass.ChainedPart;
import static com.proofpoint.event.client.EventTypeMetadata.getValidEventTypeMetaDataSet;
import static org.testng.Assert.assertEquals;

public abstract class AbstractTestJsonEventWriter
{
    protected JsonEventWriter eventWriter;

    abstract <T> void writeEvents(final Iterable<T> events, String token, OutputStream out)
            throws Exception;

    @BeforeMethod
    public void setup()
            throws Exception
    {
        Set<EventTypeMetadata<?>> eventTypes = getValidEventTypeMetaDataSet(
                FixedDummyEventClass.class, NestedDummyEventClass.class, CircularEventClass.class, ChainedCircularEventClass.class);
        eventWriter = new JsonEventWriter(new NodeInfo("test"), eventTypes);
    }

    @Test
    public void testEventWriter()
            throws Exception
    {
        assertEventJson(TestingUtils.getEvents(), "sample-trace-token", "events.json");
    }

    @Test
    public void testNullValue()
            throws Exception
    {
        FixedDummyEventClass event = new FixedDummyEventClass(
                "localhost", new DateTime("2011-09-09T01:59:59.999Z"), UUID.fromString("1ea8ca34-db36-11e0-b76f-8b7d505ab1ad"), 123, null);

        assertEventJson(ImmutableList.of(event), "sample-trace-token", "nullValue.json");
    }

    @Test
    public void testNullToken()
            throws Exception
    {
        assertEventJson(TestingUtils.getEvents(), null, "nullToken.json");
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

        assertEventJson(ImmutableList.of(nestedEvent), "sample-trace-token", "nested.json");
    }

    @Test(expectedExceptions = InvalidEventException.class, expectedExceptionsMessageRegExp = "Cycle detected in event data:.*")
    public void testCircularEvent()
            throws Exception
    {
        writeEvents(ImmutableList.of(new CircularEventClass()), null, nullOutputStream());
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

        writeEvents(ImmutableList.of(event), null, nullOutputStream());
    }

    @Test(expectedExceptions = InvalidEventException.class)
    public void testUnregisteredEventClass()
            throws Exception
    {
        DummyEventClass event = new DummyEventClass(1.1, 1, "foo", false);
        writeEvents(ImmutableList.of(event), null, nullOutputStream());
    }

    private <T> void assertEventJson(Iterable<T> events, String token, String resource)
            throws Exception
    {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        writeEvents(events, token, out);

        String json = out.toString(Charsets.UTF_8.name());
        assertEquals(json, TestingUtils.getNormalizedJson(resource));
    }
}
