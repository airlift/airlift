package io.airlift.discovery.client;

import org.testng.annotations.Test;

import java.net.URI;
import java.util.Optional;

import static io.airlift.discovery.client.HttpDiscoveryLookupClient.createServiceLocation;
import static org.testng.Assert.assertEquals;

public class TestHttpDiscoveryLookupClient
{
    @Test
    public void testCreateServiceLocation()
    {
        URI expected = URI.create("http://example.com:8080/v1/service/abc");
        assertEquals(createServiceLocation(URI.create("http://example.com:8080"), "abc", Optional.empty()), expected);
        assertEquals(createServiceLocation(URI.create("http://example.com:8080/"), "abc", Optional.empty()), expected);

        expected = URI.create("https://example.com:8080/v1/service/abc");
        assertEquals(createServiceLocation(URI.create("https://example.com:8080"), "abc", Optional.empty()), expected);
        assertEquals(createServiceLocation(URI.create("https://example.com:8080/"), "abc", Optional.empty()), expected);

        expected = URI.create("http://example.com:8080/v1/service/abc/xyz");
        assertEquals(createServiceLocation(URI.create("http://example.com:8080"), "abc", Optional.of("xyz")), expected);
        assertEquals(createServiceLocation(URI.create("http://example.com:8080/"), "abc", Optional.of("xyz")), expected);
    }
}
