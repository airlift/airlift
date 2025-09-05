package io.airlift.api.servertests.quota;

import io.airlift.api.servertests.ServerTestBase;
import io.airlift.bootstrap.ApplicationConfigurationException;
import io.airlift.http.client.HttpStatus;
import io.airlift.http.client.Request;
import io.airlift.json.JsonCodec;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.UriBuilder;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Optional;

import static io.airlift.http.client.JsonBodyGenerator.jsonBodyGenerator;
import static io.airlift.http.client.Request.Builder.preparePost;
import static io.airlift.http.client.StatusResponseHandler.createStatusResponseHandler;
import static io.airlift.json.JsonCodec.jsonCodec;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class TestQuotas
        extends ServerTestBase
{
    private static final JsonCodec<QuotaResource> QUOTA_RESOURCE_CODEC = jsonCodec(QuotaResource.class);

    public TestQuotas()
    {
        super(QuotaService.class);
    }

    @Test
    public void testCorrect()
    {
        assertThat(doRequest(Optional.empty())).isEqualTo(HttpStatus.NO_CONTENT.code());
    }

    @Test
    public void testCorrectButThrows()
    {
        assertThat(doRequest(Optional.of("throws"))).isEqualTo(HttpStatus.BAD_REQUEST.code());
    }

    @Test
    public void testCorrectBeta()
    {
        assertThat(doRequest(Optional.of("goodBeta"))).isEqualTo(HttpStatus.NO_CONTENT.code());
    }

    @Test
    public void testIncorrect()
    {
        assertThat(doRequest(Optional.of("badly"))).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.code());
    }

    private static class BadQuotasServer
            extends ServerTestBase
    {
        public BadQuotasServer()
        {
            super(BadQuotaService.class);
        }
    }

    @Test
    public void testMissingQuotas()
    {
        assertThatThrownBy(BadQuotasServer::new).isInstanceOf(ApplicationConfigurationException.class);
    }

    @Test
    public void testUnspecifiedQuotas()
    {
        assertThat(doRequest(Optional.of("unspecified"))).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.code());
    }

    private int doRequest(Optional<String> verb)
    {
        URI tempUri = UriBuilder.fromUri(baseUri).path("public/api/v1/kwota/{kwotaId}").build("xyz");
        URI uri = verb.map(v -> URI.create(tempUri.toString() + ":" + v)).orElse(tempUri);

        Request request = preparePost()
                .setUri(uri)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
                .setBodyGenerator(jsonBodyGenerator(QUOTA_RESOURCE_CODEC, new QuotaResource("dummy")))
                .build();
        return httpClient.execute(request, createStatusResponseHandler()).getStatusCode();
    }
}
