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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Resources;
import org.joda.time.DateTime;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import static java.nio.charset.StandardCharsets.UTF_8;

class TestingUtils
{
    public static List<FixedDummyEventClass> getEvents()
    {
        return ImmutableList.of(
                new FixedDummyEventClass("localhost", new DateTime("2011-09-09T01:35:28.333Z"), UUID.fromString("8e248a16-da86-11e0-9e77-9fc96e21a396"), 5678, "foo"),
                new FixedDummyEventClass("localhost", new DateTime("2011-09-09T01:43:18.123Z"), UUID.fromString("94ac328a-da86-11e0-afe9-d30a5b7c4f68"), 1, "bar"),
                new FixedDummyEventClass("localhost", new DateTime("2011-09-09T01:45:55.555Z"), UUID.fromString("a30671a6-da86-11e0-bc43-971987242263"), 1234, "hello")
        );
    }

    public static String getNormalizedJson(String resource)
            throws IOException
    {
        String json = Resources.toString(Resources.getResource(resource), UTF_8);
        ObjectMapper mapper = new ObjectMapper();
        return mapper.writeValueAsString(mapper.readValue(json, Object.class));
    }
}
