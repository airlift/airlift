package io.airlift.api.servertests.streaming;

import io.airlift.api.servertests.ServerTestBase;
import io.airlift.http.client.Request;
import io.airlift.http.client.StatusResponseHandler.StatusResponse;
import io.airlift.http.client.StringResponseHandler.StringResponse;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.UriBuilder;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Optional;

import static io.airlift.http.client.Request.Builder.prepareGet;
import static io.airlift.http.client.StatusResponseHandler.createStatusResponseHandler;
import static io.airlift.http.client.StringResponseHandler.createStringResponseHandler;
import static org.assertj.core.api.Assertions.assertThat;

public class TestStreaming
        extends ServerTestBase
{
    public TestStreaming()
    {
        super(StreamingService.class);
    }

    @Test
    public void testStreaming()
    {
        StringResponse stringResponse = doRequest(Optional.empty());
        assertThat(stringResponse.getHeader("Content-Type")).isEqualTo(MediaType.APPLICATION_OCTET_STREAM);
        assertThat(stringResponse.getBody()).isEqualTo("This is streaming bytes");

        stringResponse = doRequest(Optional.of("chars"));
        assertThat(stringResponse.getHeader("Content-Type")).isEqualTo(MediaType.TEXT_PLAIN + ";charset=iso-8859-1");
        assertThat(stringResponse.getBody()).isEqualTo("This is streaming chars");

        stringResponse = doRequest(Optional.of("output"));
        assertThat(stringResponse.getHeader("Content-Type")).isEqualTo(MediaType.APPLICATION_OCTET_STREAM);
        assertThat(stringResponse.getHeader("Content-Disposition")).isEqualTo("attachment; filename=\"foo.bar\"");
        assertThat(stringResponse.getBody()).isEqualTo("This is streaming output");
    }

    @Test
    public void testStreamingBadRequest()
    {
        Request request = buildRequest(Optional.of("bad"));
        StatusResponse response = httpClient.execute(request, createStatusResponseHandler());
        assertThat(response.getHeader("Content-Type")).isEqualTo("application/json");
        assertThat(response.getStatusCode()).isEqualTo(401);
    }

    private StringResponse doRequest(Optional<String> verb)
    {
        Request request = buildRequest(verb);
        return httpClient.execute(request, createStringResponseHandler());
    }

    private Request buildRequest(Optional<String> verb)
    {
        URI tempUri = UriBuilder.fromUri(baseUri).path("public/api/v1/streamer").build();
        URI uri = verb.map(v -> URI.create(tempUri.toString() + ":" + v)).orElse(tempUri);

        return prepareGet().setUri(uri).build();
    }
}
