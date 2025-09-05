package io.airlift.api.servertests.filters;

import io.airlift.api.servertests.ServerTestBase;
import io.airlift.http.client.Request;
import io.airlift.http.client.StatusResponseHandler.StatusResponse;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.UriBuilder;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static io.airlift.http.client.Request.Builder.prepareGet;
import static io.airlift.http.client.StatusResponseHandler.createStatusResponseHandler;
import static org.assertj.core.api.Assertions.assertThat;

public class TestFiltering
        extends ServerTestBase
{
    public TestFiltering()
    {
        super(FilterService.class);
    }

    @Test
    public void testFiltering()
    {
        assertThat(counters).isEmpty();

        request(false);
        assertThat(convert(counters)).containsExactly(Map.entry("response", 1));
        request(false);
        assertThat(convert(counters)).containsExactly(Map.entry("response", 2));
        counters.clear();

        request(true);
        assertThat(convert(counters)).containsExactly(Map.entry("request", 1));
        request(true);
        assertThat(convert(counters)).containsExactly(Map.entry("request", 2));
    }

    private void request(boolean withId)
    {
        UriBuilder uriBuilder = UriBuilder.fromUri(baseUri).path("public/api/v1/thing");
        if (withId) {
            uriBuilder.path("boo");
        }
        URI uri = uriBuilder.build();
        Request request = prepareGet()
                .setUri(uri)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
                .build();
        StatusResponse response = httpClient.execute(request, createStatusResponseHandler());
        assertThat(response.getStatusCode()).isLessThanOrEqualTo(299);
    }

    private Map<String, Integer> convert(Map<String, AtomicInteger> map)
    {
        return map.entrySet().stream().collect(toImmutableMap(Map.Entry::getKey, entry -> entry.getValue().get()));
    }
}
