package io.airlift.api.servertests.patch;

import com.google.common.collect.ImmutableSet;
import io.airlift.api.ApiBuilderConfig;
import io.airlift.api.ApiPatch;
import io.airlift.api.builders.ApiBuilder;
import io.airlift.api.model.ModelService;
import io.airlift.api.model.ModelServices;
import io.airlift.api.openapi.OpenApiMetadata;
import io.airlift.api.openapi.OpenApiMetadata.OpenApiVersion;
import io.airlift.api.openapi.OpenApiProvider;
import io.airlift.api.servertests.ServerTestBase;
import io.airlift.api.validation.ValidatorException;
import io.airlift.http.client.FullJsonResponseHandler.JsonResponse;
import io.airlift.http.client.Request;
import io.airlift.json.JsonCodec;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.UriBuilder;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static io.airlift.api.openapi.OpenApiMetadata.OpenApiVersion.OPENAPI_3_0_1;
import static io.airlift.api.openapi.OpenApiMetadata.OpenApiVersion.OPENAPI_3_2_0;
import static io.airlift.http.client.FullJsonResponseHandler.createFullJsonResponseHandler;
import static io.airlift.http.client.HeaderNames.CONTENT_TYPE;
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

        assertThat(ApiPatch.apply(oldValue, ImmutableSet.of(), (_, _) -> null)).isEqualTo(oldValue);
        assertThat(ApiPatch.apply(oldValue, ImmutableSet.of("name", "qty"), (name, _) -> switch (name) {
            case "name" -> newValue.name;
            case "qty" -> newValue.qty;
            default -> throw new IllegalArgumentException();
        })).isEqualTo(newValue);
        assertThat(ApiPatch.apply(oldValue, ImmutableSet.of("name"), (_, _) -> newValue.name)).isEqualTo(new Something(newValue.name, oldValue.qty));
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

    @Test
    public void testOptionalFieldsAreNullableInOpenApi301()
    {
        JsonNode properties = packagePatchProperties(OPENAPI_3_0_1);
        assertThat(properties.get("notes")).isEqualTo(jsonMapper.readTree(
                """
                {"type": "string", "description": "dummy", "nullable": true}
                """));
        assertThat(properties.get("state")).isEqualTo(jsonMapper.readTree(
                """
                {"anyOf": [{"$ref": "#/components/schemas/ItemState"}, {"type": "string", "nullable": true, "enum": [null]}], "description": "dummy"}
                """));
        assertThat(properties.get("label")).isEqualTo(jsonMapper.readTree(
                """
                {"anyOf": [{"$ref": "#/components/schemas/LabelPatch"}, {"type": "string", "nullable": true, "enum": [null]}]}
                """));
        assertThat(properties.get("veryImportantThing")).isEqualTo(jsonMapper.readTree(
                """
                {"type": "string", "description": "dummy"}
                """));
    }

    @Test
    public void testOptionalFieldsAreNullableInOpenApi320()
    {
        JsonNode properties = packagePatchProperties(OPENAPI_3_2_0);
        assertThat(properties.get("notes")).isEqualTo(jsonMapper.readTree(
                """
                {"anyOf": [{"type": "string"}, {"type": "null"}], "description": "dummy"}
                """));
        assertThat(properties.get("state")).isEqualTo(jsonMapper.readTree(
                """
                {"anyOf": [{"$ref": "#/components/schemas/ItemState"}, {"type": "null"}], "description": "dummy"}
                """));
        assertThat(properties.get("label")).isEqualTo(jsonMapper.readTree(
                """
                {"anyOf": [{"$ref": "#/components/schemas/LabelPatch"}, {"type": "null"}]}
                """));
        assertThat(properties.get("veryImportantThing")).isEqualTo(jsonMapper.readTree(
                """
                {"type": "string", "description": "dummy"}
                """));
    }

    private JsonNode packagePatchProperties(OpenApiVersion version)
    {
        ModelServices modelServices = ApiBuilder.apiBuilder().add(PackageService.class).build().modelServices();
        ModelService service = modelServices.services().iterator().next();
        OpenApiProvider provider = OpenApiProvider.create(modelServices, new OpenApiMetadata(Optional.empty(), List.of(), "/", Duration.ofMinutes(5), version), ApiBuilderConfig.jackson());
        return jsonMapper.readTree(jsonMapper.writeValueAsString(provider.build(service.service().type(), _ -> true))).at("/components/schemas/PackagePatch/properties");
    }

    private JsonResponse<Fields> doRequest(String json)
    {
        URI uri = UriBuilder.fromUri(baseUri).path("public/api/v1/package").build();
        Request request = preparePatch()
                .setUri(uri)
                .setHeader(CONTENT_TYPE, MediaType.APPLICATION_JSON)
                .setBodyGenerator(createStaticBodyGenerator(json, StandardCharsets.UTF_8))
                .build();
        return httpClient.execute(request, createFullJsonResponseHandler(FIELDS_JSON_CODEC));
    }
}
