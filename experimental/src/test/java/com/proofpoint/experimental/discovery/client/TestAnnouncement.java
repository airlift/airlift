package com.proofpoint.experimental.discovery.client;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.io.Resources;
import com.proofpoint.experimental.json.JsonCodec;
import junit.framework.TestCase;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

import static com.proofpoint.experimental.discovery.client.ServiceAnnouncement.serviceAnnouncement;
import static com.proofpoint.experimental.json.JsonCodec.jsonCodec;
import static com.proofpoint.experimental.json.JsonCodec.mapJsonCodec;
import static com.proofpoint.testing.EquivalenceTester.equivalenceTester;

public class TestAnnouncement extends TestCase
{
    private final JsonCodec<Announcement> announcementCodec = jsonCodec(Announcement.class);
    private final JsonCodec<Map<String, Object>> objectCodec = mapJsonCodec(String.class, Object.class);

    @Test
    public void testJsonEncode()
            throws Exception
    {
        Announcement announcement = new Announcement("environment", "node", "location", ImmutableSet.of(
                serviceAnnouncement("foo")
                        .addProperty("http", "http://localhost:8080")
                        .addProperty("jmx", "jmx://localhost:1234")
                        .build())
        );
        Map<String, Object> actual = objectCodec.fromJson(announcementCodec.toJson(announcement));

        String json = Resources.toString(Resources.getResource("announcement.json"), Charsets.UTF_8);
        Map<String, Object> expected = objectCodec.fromJson(json);

        // set id in expected
        List<Map<String, Object>> services = (List<Map<String, Object>>) expected.get("services");
        services.get(0).put("id", Iterables.getOnlyElement(announcement.getServices()).getId().toString());

        assertEquals(actual, expected);
    }

    @Test
    public void testEquivalence()
    {
        equivalenceTester()
                .addEquivalentGroup(
                        new Announcement("environment", "node-A", "location", ImmutableSet.<ServiceAnnouncement>of(serviceAnnouncement("foo").build())),
                        new Announcement("ENVIRONMENT", "node-A", "location", ImmutableSet.<ServiceAnnouncement>of(serviceAnnouncement("foo").build())),
                        new Announcement("environment", "node-A", "LOCATION", ImmutableSet.<ServiceAnnouncement>of(serviceAnnouncement("foo").build())),
                        new Announcement("environment", "node-A", "location", ImmutableSet.<ServiceAnnouncement>of(serviceAnnouncement("FOO").build()))
                )
                .addEquivalentGroup(
                        new Announcement("environment", "node-B", "location", ImmutableSet.<ServiceAnnouncement>of(serviceAnnouncement("foo").build())),
                        new Announcement("environment-X", "node-B", "location", ImmutableSet.<ServiceAnnouncement>of(serviceAnnouncement("foo").build())),
                        new Announcement("environment", "node-B", "location-X", ImmutableSet.<ServiceAnnouncement>of(serviceAnnouncement("foo").build())),
                        new Announcement("environment", "node-B", "location", ImmutableSet.<ServiceAnnouncement>of(serviceAnnouncement("bar").build()))
                )
                .check();
    }
}
