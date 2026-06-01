package io.airlift.api.servertests.streaming;

import com.fasterxml.jackson.databind.JsonNode;
import io.airlift.api.builders.ApiBuilder;
import io.airlift.api.model.ModelService;
import io.airlift.api.model.ModelServices;
import io.airlift.api.openapi.OpenApiMetadata;
import io.airlift.api.openapi.OpenApiProvider;
import io.airlift.api.servertests.ServerTestBase;
import io.airlift.http.client.Request;
import io.airlift.http.client.StatusResponseHandler.StatusResponse;
import io.airlift.http.client.StringResponseHandler.StringResponse;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.UriBuilder;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static io.airlift.api.openapi.OpenApiMetadata.OpenApiVersion.OPENAPI_3_0_1;
import static io.airlift.api.openapi.OpenApiMetadata.OpenApiVersion.OPENAPI_3_2_0;
import static io.airlift.http.client.HeaderNames.ACCEPT;
import static io.airlift.http.client.HeaderNames.CONTENT_DISPOSITION;
import static io.airlift.http.client.HeaderNames.CONTENT_TYPE;
import static io.airlift.http.client.JsonBodyGenerator.jsonBodyGenerator;
import static io.airlift.http.client.Request.Builder.prepareGet;
import static io.airlift.http.client.Request.Builder.preparePost;
import static io.airlift.http.client.StatusResponseHandler.createStatusResponseHandler;
import static io.airlift.http.client.StringResponseHandler.createStringResponseHandler;
import static io.airlift.json.JsonCodec.jsonCodec;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

public class TestStreaming
        extends ServerTestBase
{
    public TestStreaming()
    {
        super(StreamingService.class, builder -> builder.withOpenApiMetadata(new OpenApiMetadata(Optional.empty(), List.of(), "/", Duration.ofMinutes(5), OPENAPI_3_0_1)));
    }

    @Test
    public void testStreaming()
    {
        StringResponse stringResponse = doRequest(Optional.empty());
        assertThat(stringResponse.getHeader(CONTENT_TYPE)).hasValue(MediaType.APPLICATION_OCTET_STREAM);
        assertThat(stringResponse.getBody()).isEqualTo("This is streaming bytes");

        stringResponse = doRequest(Optional.of("chars"));
        assertThat(stringResponse.getHeader(CONTENT_TYPE)).hasValue(MediaType.TEXT_PLAIN + ";charset=iso-8859-1");
        assertThat(stringResponse.getBody()).isEqualTo("This is streaming chars");

        stringResponse = doRequest(Optional.of("output"));
        assertThat(stringResponse.getHeader(CONTENT_TYPE)).hasValue(MediaType.APPLICATION_OCTET_STREAM);
        assertThat(stringResponse.getHeader(CONTENT_DISPOSITION)).hasValue("attachment; filename=\"foo.bar\"");
        assertThat(stringResponse.getBody()).isEqualTo("This is streaming output");

        stringResponse = doRequest(Optional.of("events"));
        assertThat(stringResponse.getHeader(CONTENT_TYPE)).hasValue("text/event-stream");
        assertThat(stringResponse.getBody()).isEqualTo("data: {\"type\":\"message\",\"message\":\"hello\"}\n\n");
    }

    @Test
    public void testPostStreaming()
    {
        URI uri = UriBuilder.fromUri(baseUri).path("public/api/v1/streamer:postEvents").build();
        Request request = preparePost().setUri(uri).build();
        StringResponse stringResponse = httpClient.execute(request, createStringResponseHandler());

        assertThat(stringResponse.getHeader(CONTENT_TYPE)).hasValue("text/event-stream");
        assertThat(stringResponse.getBody()).isEqualTo("data: {\"type\":\"message\",\"message\":\"posted\"}\n\n");
    }

    @Test
    public void testDefaultOpenApiStreaming()
            throws Exception
    {
        JsonNode openApi = getOpenApi();
        assertThat(openApi.at("/openapi").asText()).isEqualTo("3.0.1");

        JsonNode outputMediaType = openApi.at("/paths/~1public~1api~1v1~1streamer:output/get/responses/200/content/application~1octet-stream");
        assertThat(outputMediaType.at("/schema/type").asText()).isEqualTo("string");
        assertThat(outputMediaType.has("x-airlift-event-schema")).isFalse();

        JsonNode eventMediaType = openApi.at("/paths/~1public~1api~1v1~1streamer:events/get/responses/200/content/text~1event-stream");
        assertThat(eventMediaType.at("/schema/type").asText()).isEqualTo("string");
        assertThat(eventMediaType.at("/x-airlift-event-schema/$ref").asText()).isEqualTo("#/components/schemas/StreamingEvent");
        assertThat(openApi.at("/components/schemas/StreamingEvent").isMissingNode()).isFalse();
    }

    @Test
    public void testOpenApi32Streaming()
            throws Exception
    {
        ModelServices modelServices = ApiBuilder.apiBuilder().add(StreamingService.class).build().modelServices();
        assertThat(modelServices.errors()).isEmpty();
        ModelService service = modelServices.services().iterator().next();
        OpenApiProvider provider = OpenApiProvider.create(modelServices, new OpenApiMetadata(Optional.empty(), List.of(), "/", Duration.ofMinutes(5), OPENAPI_3_2_0));
        JsonNode openApi = jsonMapper.readTree(jsonMapper.writeValueAsString(provider.build(service.service().type(), _ -> true)));

        assertThat(openApi.at("/openapi").asText()).isEqualTo("3.2.0");
        JsonNode eventMediaType = openApi.at("/paths/~1public~1api~1v1~1streamer:events/get/responses/200/content/text~1event-stream");
        assertThat(eventMediaType.has("schema")).isFalse();
        assertThat(eventMediaType.at("/itemSchema/type").asText()).isEqualTo("object");
        assertThat(eventMediaType.at("/itemSchema/required/0").asText()).isEqualTo("data");
        assertThat(eventMediaType.at("/itemSchema/properties/data/type").asText()).isEqualTo("string");
        assertThat(eventMediaType.at("/itemSchema/properties/data/contentMediaType").asText()).isEqualTo("application/json");
        assertThat(eventMediaType.at("/itemSchema/properties/data/contentSchema/$ref").asText()).isEqualTo("#/components/schemas/StreamingEvent");
        assertThat(eventMediaType.at("/itemSchema/properties/event").isMissingNode()).isTrue();
    }

    @Test
    public void testStreamingPost()
    {
        URI uri = UriBuilder.fromUri(baseUri).path("public/api/v1/streamer:post").build();
        Request request = preparePost()
                .setUri(uri)
                .setHeader(ACCEPT, MediaType.TEXT_PLAIN)
                .setHeader(CONTENT_TYPE, MediaType.APPLICATION_JSON)
                .setBodyGenerator(jsonBodyGenerator(jsonCodec(StreamingRequest.class), new StreamingRequest("payload")))
                .build();

        StringResponse stringResponse = httpClient.execute(request, createStringResponseHandler());
        assertThat(stringResponse.getStatusCode()).isEqualTo(200);
        assertThat(stringResponse.getHeader(CONTENT_TYPE)).hasValue(MediaType.TEXT_PLAIN + ";charset=iso-8859-1");
        assertThat(stringResponse.getBody()).isEqualTo("This is streaming post: payload");
    }

    @Test
    public void testStreamingBadRequest()
    {
        Request request = buildRequest(Optional.of("bad"));
        StatusResponse response = httpClient.execute(request, createStatusResponseHandler());
        assertThat(response.getHeader(CONTENT_TYPE)).hasValue("application/json");
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

    private JsonNode getOpenApi()
            throws Exception
    {
        URI uri = UriBuilder.fromUri(baseUri).path("/public/openapi/v1/json").build();
        Request request = prepareGet().setUri(uri).build();
        StringResponse response = httpClient.execute(request, createStringResponseHandler());

        assertThat(response.getStatusCode()).isEqualTo(200);
        return jsonMapper.readTree(response.getBody().getBytes(UTF_8));
    }
}
