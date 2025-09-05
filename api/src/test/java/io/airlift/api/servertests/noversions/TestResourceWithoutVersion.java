package io.airlift.api.servertests.noversions;

import io.airlift.api.servertests.ServerTestBase;
import io.airlift.http.client.Request;
import io.airlift.json.JsonCodec;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.UriBuilder;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static io.airlift.http.client.HttpStatus.NO_CONTENT;
import static io.airlift.http.client.JsonBodyGenerator.jsonBodyGenerator;
import static io.airlift.http.client.Request.Builder.preparePut;
import static io.airlift.http.client.StatusResponseHandler.createStatusResponseHandler;
import static io.airlift.json.JsonCodec.jsonCodec;

public class TestResourceWithoutVersion
        extends ServerTestBase
{
    private static final JsonCodec<ResourceWithoutVersion> RESOURCE_WITHOUT_VERSION_CODEC = jsonCodec(ResourceWithoutVersion.class);

    public TestResourceWithoutVersion()
    {
        super(ResourceService.class);
    }

    @Test
    public void test()
    {
        ResourceWithoutVersion resource = new ResourceWithoutVersion(new ResourceId("1"), "no version");
        URI uri = UriBuilder.fromUri(baseUri).path("public/api/v1/resource").build();

        Request request = preparePut()
                .setUri(uri)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
                .setBodyGenerator(jsonBodyGenerator(RESOURCE_WITHOUT_VERSION_CODEC, resource))
                .build();
        int statusCode = httpClient.execute(request, createStatusResponseHandler()).getStatusCode();
        Assertions.assertThat(statusCode).isEqualTo(NO_CONTENT.code());
    }
}
