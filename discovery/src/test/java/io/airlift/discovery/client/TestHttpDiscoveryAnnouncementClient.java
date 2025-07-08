package io.airlift.discovery.client;

import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.URI;
import java.util.Optional;

import static io.airlift.discovery.client.HttpDiscoveryAnnouncementClient.createAnnouncementLocation;
import static io.airlift.discovery.client.HttpDiscoveryAnnouncementClient.reportChangedAddressResolution;
import static org.assertj.core.api.Assertions.assertThat;

public class TestHttpDiscoveryAnnouncementClient
{
    @Test
    public void testCreateAnnouncementLocation()
    {
        URI expected = URI.create("http://example.com:8080/v1/announcement/abc");
        assertThat(createAnnouncementLocation(URI.create("http://example.com:8080"), "abc")).isEqualTo(expected);
        assertThat(createAnnouncementLocation(URI.create("http://example.com:8080/"), "abc")).isEqualTo(expected);

        expected = URI.create("https://example.com:8080/v1/announcement/abc");
        assertThat(createAnnouncementLocation(URI.create("https://example.com:8080"), "abc")).isEqualTo(expected);
        assertThat(createAnnouncementLocation(URI.create("https://example.com:8080/"), "abc")).isEqualTo(expected);
    }

    @Test
    public void testResolutionFailed()
    {
        URI badUri = URI.create("https://example.bad:8080/");
        Optional<InetAddress> result = reportChangedAddressResolution(badUri, Optional.empty(), false);
        assertThat(result).isEmpty();
    }

    @Test
    public void testPortChangeIgnored()
    {
        URI uri1 = URI.create("https://example.com:8080/");
        URI uri2 = URI.create("https://example.com:80/");
        Optional<InetAddress> success = reportChangedAddressResolution(uri1, Optional.empty(), true);
        assertThat(success).isNotEmpty();
        assertThat(reportChangedAddressResolution(uri2, success, true)).isEqualTo(success);
    }

    @Test
    public void testSchemeChangeIgnored()
    {
        URI uri1 = URI.create("http://example.com:8080/");
        URI uri2 = URI.create("https://example.com:8080/");
        Optional<InetAddress> success = reportChangedAddressResolution(uri1, Optional.empty(), true);
        assertThat(success).isNotEmpty();
        assertThat(reportChangedAddressResolution(uri2, success, true)).isEqualTo(success);
    }

    @Test
    public void testPathChangeIgnored()
    {
        URI uri1 = URI.create("http://example.com:8080/api/v1/announcement/abc");
        URI uri2 = URI.create("https://example.com:8080/");
        Optional<InetAddress> success = reportChangedAddressResolution(uri1, Optional.empty(), true);
        assertThat(success).isNotEmpty();
        assertThat(reportChangedAddressResolution(uri2, success, true)).isEqualTo(success);
    }

    @Test
    public void testAddressChangeDetected()
    {
        URI uri1 = URI.create("https://example.com:8080/");
        URI uri2 = URI.create("https://google.com:80/");
        Optional<InetAddress> firstResolution = reportChangedAddressResolution(uri1, Optional.empty(), true);
        assertThat(firstResolution).isNotEmpty();
        assertThat(reportChangedAddressResolution(uri2, firstResolution, true)).isNotEqualTo(firstResolution);
    }
}
