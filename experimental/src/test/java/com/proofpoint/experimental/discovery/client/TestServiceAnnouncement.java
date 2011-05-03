package com.proofpoint.experimental.discovery.client;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Resources;
import com.proofpoint.experimental.discovery.client.ServiceAnnouncement.ServiceAnnouncementBuilder;
import com.proofpoint.experimental.json.JsonCodec;
import org.testng.annotations.Test;

import java.util.Map;

import static com.proofpoint.experimental.discovery.client.ServiceAnnouncement.serviceAnnouncement;
import static com.proofpoint.experimental.discovery.client.ServiceSelectorConfig.DEFAULT_POOL;
import static com.proofpoint.experimental.json.JsonCodec.jsonCodec;
import static com.proofpoint.experimental.json.JsonCodec.mapJsonCodec;
import static com.proofpoint.testing.EquivalenceTester.equivalenceTester;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

public class TestServiceAnnouncement
{
    private final JsonCodec<ServiceAnnouncement> serviceAnnouncementCodec = jsonCodec(ServiceAnnouncement.class);
    private final JsonCodec<Map<String, Object>> objectCodec = mapJsonCodec(String.class, Object.class);

    @Test
    public void testBuilder()
    {
        assertAnnouncement(serviceAnnouncement("foo").build(), "foo", DEFAULT_POOL, ImmutableMap.<String, String>of());
        assertAnnouncement(serviceAnnouncement("foo").setPool("pool").build(), "foo", "pool", ImmutableMap.<String, String>of());

        assertAnnouncement(serviceAnnouncement("foo").setPool("pool").addProperty("a", "apple").build(),
                "foo",
                "pool",
                ImmutableMap.of("a", "apple"));

        assertAnnouncement(serviceAnnouncement("foo").addProperties(ImmutableMap.of("a", "apple", "b", "banana")).build(),
                "foo",
                DEFAULT_POOL,
                ImmutableMap.of("a", "apple", "b", "banana"));
    }

    private void assertAnnouncement(ServiceAnnouncement announcement, String type, String pool, Map<String, String> properties)
    {
        assertNotNull(announcement.getId());
        assertEquals(announcement.getType(), type);
        assertEquals(announcement.getPool(), pool);
        assertEquals(announcement.getProperties(), properties);
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

        String json = Resources.toString(Resources.getResource("service-announcement.json"), Charsets.UTF_8);
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
