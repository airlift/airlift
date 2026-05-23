package io.airlift.api.servertests.exceptions;

import com.google.common.collect.ImmutableList;
import io.airlift.api.responses.ApiParsedResponse;
import io.airlift.api.servertests.ServerTestBase;
import io.airlift.http.client.FullJsonResponseHandler.JsonResponse;
import io.airlift.http.client.Request;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.UriBuilder;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Optional;

import static io.airlift.http.client.FullJsonResponseHandler.createFullJsonResponseHandler;
import static io.airlift.http.client.Request.Builder.preparePost;
import static io.airlift.http.client.Request.Builder.preparePut;
import static io.airlift.json.JsonCodec.jsonCodec;
import static org.assertj.core.api.Assertions.assertThat;

public class ExceptionTest
        extends ServerTestBase
{
    public ExceptionTest()
    {
        super(ExceptionApi.class);
    }

    @Test
    public void testKnownException()
    {
        UriBuilder uriBuilder = UriBuilder.fromUri(baseUri).path("public/api/v1");
        URI uri = uriBuilder.build();
        Request request = preparePut()
                .setUri(uri)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
                .build();
        JsonResponse<Void> response = httpClient.execute(request, createFullJsonResponseHandler(jsonCodec(Void.class)));
        Optional<ApiParsedResponse> parsed = ApiParsedResponse.parse(response.getResponseBytes());
        assertThat(parsed).contains(new ApiParsedResponse("This is the message", Optional.empty(), ImmutableList.of("field1", "field2")));
    }

    @Test
    public void testUnknownException()
    {
        UriBuilder uriBuilder = UriBuilder.fromUri(baseUri).path("public/api/v1");
        URI uri = uriBuilder.build();
        Request request = preparePost()
                .setUri(uri)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
                .build();
        JsonResponse<Void> response = httpClient.execute(request, createFullJsonResponseHandler(jsonCodec(Void.class)));
        Optional<ApiParsedResponse> parsed = ApiParsedResponse.parse(response.getResponseBytes());
        assertThat(parsed).isEmpty();
    }
}
