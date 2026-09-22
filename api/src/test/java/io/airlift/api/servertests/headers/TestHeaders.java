package io.airlift.api.servertests.headers;

import io.airlift.api.servertests.ServerTestBase;
import io.airlift.http.client.HeaderName;
import io.airlift.http.client.Request;
import io.airlift.http.client.StatusResponseHandler.StatusResponse;
import jakarta.ws.rs.core.UriBuilder;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Optional;

import static io.airlift.http.client.Request.Builder.prepareGet;
import static io.airlift.http.client.StatusResponseHandler.createStatusResponseHandler;
import static org.assertj.core.api.Assertions.assertThat;

public class TestHeaders
        extends ServerTestBase
{
    private static final HeaderName IDEMPOTENCY_KEY = HeaderName.of("Idempotency-Key");
    private static final HeaderName DERIVED_IDEMPOTENCY_KEY = HeaderName.of("X-IDEMPOTENCY-KEY");
    private static final HeaderName REQUEST_ID = HeaderName.of("X-REQUEST-ID");

    public TestHeaders()
    {
        super(HeaderService.class);
    }

    @Test
    public void testExplicitAndDerivedHeaderNames()
    {
        URI uri = UriBuilder.fromUri(baseUri).path("public/api/v1/thing/boo").build();

        // the explicitly named header is read under its exact name; the unnamed one under the derived X- name
        Request request = prepareGet()
                .setUri(uri)
                .setHeader(IDEMPOTENCY_KEY, "key-1")
                .setHeader(REQUEST_ID, "request-1")
                .build();
        StatusResponse response = httpClient.execute(request, createStatusResponseHandler());
        assertThat(response.getStatusCode()).isEqualTo(200);
        assertThat(HeaderService.RECEIVED_IDEMPOTENCY_KEY.get()).isEqualTo(Optional.of("key-1"));
        assertThat(HeaderService.RECEIVED_REQUEST_ID.get()).isEqualTo(Optional.of("request-1"));

        // the derived name is not accepted for the explicitly named header
        request = prepareGet()
                .setUri(uri)
                .setHeader(DERIVED_IDEMPOTENCY_KEY, "key-2")
                .build();
        response = httpClient.execute(request, createStatusResponseHandler());
        assertThat(response.getStatusCode()).isEqualTo(200);
        assertThat(HeaderService.RECEIVED_IDEMPOTENCY_KEY.get()).isEmpty();
        assertThat(HeaderService.RECEIVED_REQUEST_ID.get()).isEmpty();
    }
}
