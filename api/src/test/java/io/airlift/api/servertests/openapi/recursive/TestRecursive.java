package io.airlift.api.servertests.openapi.recursive;

import com.google.common.collect.ImmutableList;
import com.google.common.io.Resources;
import com.google.inject.Key;
import com.google.inject.TypeLiteral;
import io.airlift.api.model.ModelServiceType;
import io.airlift.api.openapi.OpenApiMetadata;
import io.airlift.api.openapi.OpenApiProvider;
import io.airlift.api.openapi.models.OpenAPI;
import io.airlift.api.servertests.ServerTestBase;
import io.airlift.json.JsonCodec;
import io.sirlift.api.generated.ComplexServiceApi;
import io.sirlift.api.generated.SimpleServiceApi;
import io.sirlift.api.generated.client.ApiClient;
import io.sirlift.api.generated.models.Poly;
import io.sirlift.api.generated.models.Simple;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.Optional;

import static io.airlift.api.servertests.openapi.TestOpenApi.validateOpenApiJson;
import static io.airlift.json.JsonCodec.jsonCodec;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

public class TestRecursive
        extends ServerTestBase
{
    private static final JsonCodec<OpenAPI> OPEN_API_CODEC = jsonCodec(OpenAPI.class);

    private final OpenApiProvider openApiProvider;
    private final Collection<ModelServiceType> modelServiceTypes;

    public TestRecursive()
    {
        super(builder -> builder
                .addApi(apiBuilder -> apiBuilder.add(ComplexRecursiveService.class))
                .addApi(apiBuilder -> apiBuilder.add(SimpleRecursiveService.class))
                .withOpenApiMetadata(new OpenApiMetadata(Optional.empty(), ImmutableList.of())));

        openApiProvider = injector.getInstance(OpenApiProvider.class);
        modelServiceTypes = injector.getInstance(Key.get(new TypeLiteral<>() {}));
    }

    @Test
    public void testOpenApiForComplexRecursive()
            throws Exception
    {
        ModelServiceType modelServiceType = modelServiceTypes.stream()
                .filter(type -> type.id().equals("public"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("ModelServiceType not found"));

        OpenAPI openAPI = openApiProvider.build(modelServiceType, _ -> true);
        String json = OPEN_API_CODEC.toJson(openAPI);

        validateOpenApiJson(json);

        String expectedJson = Resources.toString(Resources.getResource("openapi/complex-recursive.json"), UTF_8);
        assertThat(json).isEqualTo(expectedJson.strip());
    }

    @Test
    public void testOpenApiForSimpleRecursive()
            throws Exception
    {
        ModelServiceType modelServiceType = modelServiceTypes.stream()
                .filter(type -> type.id().equals("simple"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("ModelServiceType not found"));

        OpenAPI openAPI = openApiProvider.build(modelServiceType, _ -> true);
        String json = OPEN_API_CODEC.toJson(openAPI);

        validateOpenApiJson(json);

        String expectedJson = Resources.toString(Resources.getResource("openapi/simple-recursive.json"), UTF_8);
        assertThat(json).isEqualTo(expectedJson.strip());
    }

    @Test
    public void testGeneratedClient()
            throws Exception
    {
        ApiClient apiClient = new ApiClient();
        apiClient.setHost(baseUri.getHost());
        apiClient.setPort(baseUri.getPort());

        Poly poly = objectMapper.convertValue(TestModels.TEST_POLY_TEST, Poly.class);
        ComplexServiceApi complexServiceApi = new ComplexServiceApi(apiClient);
        complexServiceApi.createPoly(poly);

        Simple simple = objectMapper.convertValue(TestModels.TEST_SIMPLE_RECURSIVE, Simple.class);
        SimpleServiceApi simpleServiceApi = new SimpleServiceApi(apiClient);
        simpleServiceApi.createSimple(simple);
    }
}
