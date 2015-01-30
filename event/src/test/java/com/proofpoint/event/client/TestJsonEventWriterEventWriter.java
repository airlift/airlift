/*
 * Copyright 2015 Proofpoint, Inc.
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

import com.proofpoint.http.client.DynamicBodySource;

import java.io.OutputStream;

import static com.proofpoint.http.client.testing.BodySourceTester.writeBodySourceTo;

public class TestJsonEventWriterEventWriter
        extends AbstractTestJsonEventWriter
{
    @Override
    <T> void writeEvents(Iterable<T> events, String token, OutputStream out)
            throws Exception
    {
        writeBodySourceTo(new EventWriterBodySource<>(events, token), out);
    }

    private class EventWriterBodySource<T> implements DynamicBodySource
    {
        private final Iterable<T> events;
        private final String token;

        EventWriterBodySource(Iterable<T> events, String token)
        {
            this.events = events;
            this.token = token;
        }

        @Override
        public Writer start(OutputStream out)
                throws Exception
        {
            return eventWriter.createEventWriter(events.iterator(), token, out);
        }
    }
}
