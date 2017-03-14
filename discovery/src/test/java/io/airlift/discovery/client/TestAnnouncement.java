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
package io.airlift.discovery.client;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.io.Resources;
import io.airlift.json.JsonCodec;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

import static io.airlift.discovery.client.ServiceAnnouncement.serviceAnnouncement;
import static io.airlift.json.JsonCodec.jsonCodec;
import static io.airlift.json.JsonCodec.mapJsonCodec;
import static io.airlift.testing.EquivalenceTester.equivalenceTester;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

public class TestAnnouncement
{
    private final JsonCodec<Announcement> announcementCodec = jsonCodec(Announcement.class);
    private final JsonCodec<Map<String, Object>> objectCodec = mapJsonCodec(String.class, Object.class);

    @Test
    public void testJsonEncode()
            throws Exception
    {
        Announcement announcement = new Announcement("environment", "node", "pool", "location", ImmutableSet.of(
                serviceAnnouncement("foo")
                        .addProperty("http", "http://localhost:8080")
                        .addProperty("jmx", "jmx://localhost:1234")
                        .build())
        );
        Map<String, Object> actual = objectCodec.fromJson(announcementCodec.toJson(announcement));

        String json = Resources.toString(Resources.getResource("announcement.json"), UTF_8);
        Map<String, Object> expected = objectCodec.fromJson(json);

        // set id in expected
        List<Map<String, Object>> services = toServices(expected.get("services"));
        services.get(0).put("id", Iterables.getOnlyElement(announcement.getServices()).getId().toString());

        assertEquals(actual, expected);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> toServices(Object value)
    {
        return (List<Map<String, Object>>) value;
    }

    @Test
    public void testToString()
    {
        assertNotNull(new Announcement("environment", "node", "pool", "location", ImmutableSet.of(
                serviceAnnouncement("foo")
                        .addProperty("http", "http://localhost:8080")
                        .addProperty("jmx", "jmx://localhost:1234")
                        .build())
        ));
    }

    @Test
    public void testEquivalence()
    {
        equivalenceTester()
                .addEquivalentGroup(
                        new Announcement("environment", "node-A", "pool", "location", ImmutableSet.<ServiceAnnouncement>of(serviceAnnouncement("foo").build())),
                        new Announcement("ENVIRONMENT", "node-A", "pool", "location", ImmutableSet.<ServiceAnnouncement>of(serviceAnnouncement("foo").build())),
                        new Announcement("environment", "node-A", "pool", "LOCATION", ImmutableSet.<ServiceAnnouncement>of(serviceAnnouncement("foo").build())),
                        new Announcement("environment", "node-A", "pool", "location", ImmutableSet.<ServiceAnnouncement>of(serviceAnnouncement("FOO").build()))
                )
                .addEquivalentGroup(
                        new Announcement("environment", "node-B", "pool", "location", ImmutableSet.<ServiceAnnouncement>of(serviceAnnouncement("foo").build())),
                        new Announcement("environment-X", "node-B", "pool", "location", ImmutableSet.<ServiceAnnouncement>of(serviceAnnouncement("foo").build())),
                        new Announcement("environment", "node-B", "pool", "location-X", ImmutableSet.<ServiceAnnouncement>of(serviceAnnouncement("foo").build())),
                        new Announcement("environment", "node-B", "pool", "location", ImmutableSet.<ServiceAnnouncement>of(serviceAnnouncement("bar").build()))
                )
                .check();
    }
}
