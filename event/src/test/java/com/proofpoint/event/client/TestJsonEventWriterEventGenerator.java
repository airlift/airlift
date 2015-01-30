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

import com.proofpoint.event.client.EventClient.EventGenerator;

import java.io.IOException;
import java.io.OutputStream;

public class TestJsonEventWriterEventGenerator
    extends AbstractTestJsonEventWriter
{
    @Override
    <T> void writeEvents(final Iterable<T> events, String token, OutputStream out)
            throws IOException
    {
        eventWriter.writeEvents(new EventGenerator<T>()
        {
            @Override
            public void generate(EventClient.EventPoster<T> eventPoster)
                    throws IOException
            {
                for (T event : events) {
                    eventPoster.post(event);
                }
            }
        }, token, out);
    }
}
