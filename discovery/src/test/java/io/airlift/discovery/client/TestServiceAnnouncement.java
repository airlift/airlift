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

import com.google.common.collect.ImmutableMap;
import com.google.common.io.Resources;
import io.airlift.json.JsonCodec;
import org.testng.annotations.Test;

import java.util.Map;

import static io.airlift.discovery.client.ServiceAnnouncement.serviceAnnouncement;
import static io.airlift.json.JsonCodec.jsonCodec;
import static io.airlift.json.JsonCodec.mapJsonCodec;
import static io.airlift.testing.EquivalenceTester.equivalenceTester;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

public class TestServiceAnnouncement
{
    private final JsonCodec<ServiceAnnouncement> serviceAnnouncementCodec = jsonCodec(ServiceAnnouncement.class);
    private final JsonCodec<Map<String, Object>> objectCodec = mapJsonCodec(String.class, Object.class);

    @Test
    public void testBuilder()
    {
        assertAnnouncement(serviceAnnouncement("foo").build(), "foo", ImmutableMap.<String, String>of());
        assertAnnouncement(serviceAnnouncement("foo").build(), "foo", ImmutableMap.<String, String>of());

        assertAnnouncement(serviceAnnouncement("foo").addProperty("a", "apple").build(),
                "foo",
                ImmutableMap.of("a", "apple"));

        assertAnnouncement(serviceAnnouncement("foo").addProperties(ImmutableMap.of("a", "apple", "b", "banana")).build(),
                "foo",
                ImmutableMap.of("a", "apple", "b", "banana"));
    }

    private void assertAnnouncement(ServiceAnnouncement announcement, String type, Map<String, String> properties)
    {
        assertNotNull(announcement.getId());
        assertEquals(announcement.getType(), type);
        assertEquals(announcement.getProperties(), properties);
    }

    @Test
    public void testToString()
    {
        assertNotNull(serviceAnnouncement("foo").build());
    }

    @Test
    public void testJsonEncode()
            throws Exception
    {
        ServiceAnnouncement serviceAnnouncement = serviceAnnouncement("foo")
                .addProperty("http", "http://localhost:8080")
                .addProperty("jmx", "jmx://localhost:1234")
                .build();
        Map<String, Object> actual = objectCodec.fromJson(serviceAnnouncementCodec.toJson(serviceAnnouncement));

        String json = Resources.toString(Resources.getResource("service-announcement.json"), UTF_8);
        Map<String, Object> expected = objectCodec.fromJson(json);

        // set id in expected
        expected.put("id", serviceAnnouncement.getId().toString());

        assertEquals(actual, expected);
    }

    @Test
    public void testEquivalence()
    {

        equivalenceTester()
                .addEquivalentGroup(serviceAnnouncement("foo")
                        .addProperty("http", "http://localhost:8080")
                        .addProperty("jmx", "jmx://localhost:1234")
                        .build()
                )
                .addEquivalentGroup(serviceAnnouncement("foo")
                        .addProperty("http", "http://localhost:8080")
                        .addProperty("jmx", "jmx://localhost:1234")
                        .build()
                )
                .check();
    }
}
