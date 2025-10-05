package io.airlift.api.servertests.unwrapped;

import io.airlift.api.servertests.ServerTestBase;
import io.airlift.http.client.Request;
import io.airlift.http.client.StatusResponseHandler;
import io.airlift.json.JsonCodec;
import io.airlift.json.JsonCodecFactory;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.UriBuilder;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Instant;
import java.util.Optional;

import static io.airlift.http.client.JsonBodyGenerator.jsonBodyGenerator;
import static io.airlift.http.client.JsonResponseHandler.createJsonResponseHandler;
import static io.airlift.http.client.Request.Builder.prepareGet;
import static io.airlift.http.client.Request.Builder.preparePost;
import static io.airlift.http.client.StatusResponseHandler.createStatusResponseHandler;
import static org.assertj.core.api.Assertions.assertThat;

public class TestUnwrapped
        extends ServerTestBase
{
    private final JsonCodecFactory jsonCodecFactory;

    public TestUnwrapped()
    {
        super(UnwrappedService.class);

        jsonCodecFactory = new JsonCodecFactory(() -> objectMapper);
    }

    @Test
    public void testSerialization()
    {
        Instant now = Instant.now();
        TopLevel topLevel = new TopLevel("topLevel", 123, new ChildLevel(now, 456, Optional.of(40), new ChildChildLevel(true)));
        String json = objectMapper.writeValueAsString(topLevel);
        assertThat(json).isEqualTo("{\"name\":\"topLevel\",\"age\":123,\"timestamp\":\"%s\",\"rate\":456.0,\"scale\":40,\"flag\":true}".formatted(now));
        TopLevel readTopLevel = objectMapper.readerFor(TopLevel.class).readValue(json);
        assertThat(readTopLevel).isEqualTo(topLevel);

        ChildLevel childLevel = new ChildLevel(now, 45.54, Optional.empty(), new ChildChildLevel(false));
        json = objectMapper.writeValueAsString(childLevel);
        assertThat(json).isEqualTo("{\"timestamp\":\"%s\",\"rate\":45.54,\"flag\":false}".formatted(now));
        ChildLevel readChildLevel = objectMapper.readerFor(ChildLevel.class).readValue(json);
        assertThat(readChildLevel).isEqualTo(childLevel);
    }

    @Test
    public void testPost()
    {
        JsonCodec<TopLevel> topLevelJsonCodec = jsonCodecFactory.jsonCodec(TopLevel.class);

        TopLevel topLevel = new TopLevel("topLevel", 123, new ChildLevel(Instant.now(), 456, Optional.of(10), new ChildChildLevel(false)));

        URI uri = UriBuilder.fromUri(baseUri).path("public/api/v1/top").build();
        Request request = preparePost()
                .setUri(uri)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
                .setBodyGenerator(jsonBodyGenerator(topLevelJsonCodec, topLevel)).build();
        StatusResponseHandler.StatusResponse response = httpClient.execute(request, createStatusResponseHandler());
        assertThat(response.getStatusCode()).isEqualTo(204);
    }

    @Test
    public void testGet()
    {
        JsonCodec<TopLevelResult> topLevelJsonCodec = jsonCodecFactory.jsonCodec(TopLevelResult.class);

        TopLevelId topLevelId = new TopLevelId("heyYouGuys!!");
        URI uri = UriBuilder.fromUri(baseUri).path("public/api/v1/top/{id}").build(topLevelId);
        Request request = prepareGet().setUri(uri).build();
        TopLevelResult result = httpClient.execute(request, createJsonResponseHandler(topLevelJsonCodec));
        assertThat(result.topId()).isEqualTo(topLevelId);
    }
}
