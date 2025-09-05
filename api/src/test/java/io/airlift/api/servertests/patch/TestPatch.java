package io.airlift.api.servertests.patch;

import com.google.common.collect.ImmutableSet;
import io.airlift.api.ApiPatch;
import io.airlift.api.servertests.ServerTestBase;
import io.airlift.api.validation.ValidatorException;
import io.airlift.http.client.FullJsonResponseHandler.JsonResponse;
import io.airlift.http.client.Request;
import io.airlift.json.JsonCodec;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.UriBuilder;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static io.airlift.http.client.FullJsonResponseHandler.createFullJsonResponseHandler;
import static io.airlift.http.client.Request.Builder.preparePatch;
import static io.airlift.http.client.StaticBodyGenerator.createStaticBodyGenerator;
import static io.airlift.json.JsonCodec.jsonCodec;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class TestPatch
        extends ServerTestBase
{
    private static final JsonCodec<Fields> FIELDS_JSON_CODEC = jsonCodec(Fields.class);

    public record Something(String name, int qty) {}

    public TestPatch()
    {
        super(PackageService.class);
    }

    @Test
    public void testEmptyObject()
    {
        // test that the server interprets empty object correctly - the server merely passes back what it gets
        JsonResponse<Fields> response = doRequest("{}");
        assertThat(response.getStatusCode()).isEqualTo(200);
        assertThat(response.getValue().fields()).isEmpty();
    }

    @Test
    public void testNulls()
    {
        // test that the server interprets null field correctly - the server merely passes back what it gets
        JsonResponse<Fields> response = doRequest("{\"manifest\": null, \"mainItem\": \"main\"}");
        assertThat(response.getStatusCode()).isEqualTo(200);
        assertThat(response.getValue().fields()).containsExactly(Map.entry("manifest", "null"), Map.entry("mainItem", "main"));
    }

    @Test
    public void testValueMappingSupplier()
    {
        Something oldValue = new Something("first", 1);
        Something newValue = new Something("second", 2);

        assertThat(ApiPatch.apply(oldValue, ImmutableSet.of(), (name, type) -> null)).isEqualTo(oldValue);
        assertThat(ApiPatch.apply(oldValue, ImmutableSet.of("name", "qty"), (name, type) -> switch (name) {
            case "name" -> newValue.name;
            case "qty" -> newValue.qty;
            default -> throw new IllegalArgumentException();
        })).isEqualTo(newValue);
        assertThat(ApiPatch.apply(oldValue, ImmutableSet.of("name"), (name, type) -> newValue.name)).isEqualTo(new Something(newValue.name, oldValue.qty));
    }

    @Test
    public void testSecondLevelStrings()
    {
        assertThat(ApiPatch.buildSecondLevel("a")).isEqualTo(Map.entry("a", ""));
        assertThat(ApiPatch.buildSecondLevel("a.b")).isEqualTo(Map.entry("a", "b"));
        assertThat(ApiPatch.buildSecondLevel("")).isEqualTo(Map.entry("", ""));
        assertThatThrownBy(() -> ApiPatch.buildSecondLevel("a.b.c")).isInstanceOf(ValidatorException.class);
        assertThatThrownBy(() -> ApiPatch.buildSecondLevel(null)).isInstanceOf(NullPointerException.class);
    }

    private JsonResponse<Fields> doRequest(String json)
    {
        URI uri = UriBuilder.fromUri(baseUri).path("public/api/v1/package").build();
        Request request = preparePatch()
                .setUri(uri)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
                .setBodyGenerator(createStaticBodyGenerator(json, StandardCharsets.UTF_8))
                .build();
        return httpClient.execute(request, createFullJsonResponseHandler(FIELDS_JSON_CODEC));
    }
}
