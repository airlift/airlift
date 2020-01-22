package io.airlift.discovery.client;

import org.testng.annotations.Test;

import java.net.URI;

import static io.airlift.discovery.client.HttpDiscoveryAnnouncementClient.createAnnouncementLocation;
import static org.testng.Assert.assertEquals;

public class TestHttpDiscoveryAnnouncementClient
{
    @Test
    public void testCreateAnnouncementLocation()
    {
        String nodeId = "ffff";
        URI expected = URI.create("http://example.com:8080/v1/announcement/" + nodeId);
        assertEquals(createAnnouncementLocation(URI.create("http://example.com:8080"), nodeId), expected);
        assertEquals(createAnnouncementLocation(URI.create("http://example.com:8080/"), nodeId), expected);
    }
}
