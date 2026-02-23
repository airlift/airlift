package io.airlift.api.servertests.openapi;

import com.google.common.collect.ImmutableList;
import com.google.common.io.Resources;
import io.airlift.api.openapi.OpenApiMetadata;
import io.airlift.api.servertests.ServerTestBase;
import io.airlift.http.client.Request;
import io.airlift.http.client.StringResponseHandler.StringResponse;
import jakarta.ws.rs.core.UriBuilder;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Optional;

import static io.airlift.api.openapi.OpenApiMetadata.SecurityScheme.BEARER_ACCESS_TOKEN;
import static io.airlift.http.client.Request.Builder.prepareGet;
import static io.airlift.http.client.StringResponseHandler.createStringResponseHandler;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

public class TestOpenApi
        extends ServerTestBase
{
    private static final OpenApiMetadata OPEN_API_METADATA = new OpenApiMetadata(Optional.of(BEARER_ACCESS_TOKEN), ImmutableList.of());

    public TestOpenApi()
    {
        super(DummyService.class, builder -> builder.withOpenApiMetadata(OPEN_API_METADATA));
    }

    @Test
    public void testOpenApiJson()
            throws Exception
    {
        URI uri = UriBuilder.fromUri(baseUri).path("/public/openapi/v1/json").build();
        Request request = prepareGet().setUri(uri).build();
        StringResponse response = httpClient.execute(request, createStringResponseHandler());

        assertThat(response.getStatusCode()).isEqualTo(200);

        String actual = response.getBody();
        String expected = Resources.toString(Resources.getResource("openapi/dummy.json"), UTF_8);
        assertThat(actual.strip()).isEqualTo(expected.strip());
    }
}
