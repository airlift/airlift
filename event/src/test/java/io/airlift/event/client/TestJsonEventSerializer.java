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

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import org.testng.annotations.Test;

import java.io.ByteArrayOutputStream;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.testng.Assert.assertEquals;

public class TestJsonEventSerializer
{
    @Test
    public void testEventSerializer()
            throws Exception
    {
        JsonEventSerializer eventSerializer = new JsonEventSerializer(FixedDummyEventClass.class);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        JsonGenerator jsonGenerator = new JsonFactory().createJsonGenerator(out, JsonEncoding.UTF8);

        FixedDummyEventClass event = TestingUtils.getEvents().get(0);
        eventSerializer.serialize(event, jsonGenerator);

        String json = out.toString(UTF_8.name());
        assertEquals(json, TestingUtils.getNormalizedJson("event.json"));
    }
}
