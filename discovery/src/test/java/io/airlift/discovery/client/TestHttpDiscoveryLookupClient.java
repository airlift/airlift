package io.airlift.discovery.client;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Optional;

import static io.airlift.discovery.client.HttpDiscoveryLookupClient.createServiceLocation;
import static org.assertj.core.api.Assertions.assertThat;

public class TestHttpDiscoveryLookupClient
{
    @Test
    public void testCreateServiceLocation()
    {
        URI expected = URI.create("http://example.com:8080/v1/service/abc");
        assertThat(createServiceLocation(URI.create("http://example.com:8080"), "abc", Optional.empty())).isEqualTo(expected);
        assertThat(createServiceLocation(URI.create("http://example.com:8080/"), "abc", Optional.empty())).isEqualTo(expected);

        expected = URI.create("https://example.com:8080/v1/service/abc");
        assertThat(createServiceLocation(URI.create("https://example.com:8080"), "abc", Optional.empty())).isEqualTo(expected);
        assertThat(createServiceLocation(URI.create("https://example.com:8080/"), "abc", Optional.empty())).isEqualTo(expected);

        expected = URI.create("http://example.com:8080/v1/service/abc/xyz");
        assertThat(createServiceLocation(URI.create("http://example.com:8080"), "abc", Optional.of("xyz"))).isEqualTo(expected);
        assertThat(createServiceLocation(URI.create("http://example.com:8080/"), "abc", Optional.of("xyz"))).isEqualTo(expected);
    }
}
