package io.airlift.api.openapi;

import com.google.common.collect.ImmutableList;
import io.airlift.api.ApiBuilderConfig;
import io.airlift.api.ApiCreate;
import io.airlift.api.ApiResponse;
import io.airlift.api.ApiService;
import io.airlift.api.ServiceType;
import io.airlift.api.model.ModelApi;
import io.airlift.api.model.ModelServiceType;
import io.airlift.api.openapi.models.OpenAPI;
import io.airlift.api.openapi.models.Operation;
import io.airlift.api.openapi.models.Schema;
import io.airlift.json.JsonCodec;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static io.airlift.api.builders.ApiBuilder.apiBuilder;
import static io.airlift.api.openapi.OpenApiMetadata.OpenApiVersion.OPENAPI_3_0_1;
import static io.airlift.api.servertests.openapi.TestOpenApi.validateOpenApiJson;
import static io.airlift.http.client.HttpStatus.CONFLICT;
import static io.airlift.json.JsonCodec.jsonCodec;
import static org.assertj.core.api.Assertions.assertThat;

public class TestOpenApiResponseStatuses
{
    private static final JsonCodec<OpenAPI> OPEN_API_CODEC = jsonCodec(OpenAPI.class);

    @Test
    public void testDeclaredResponseSharingDefaultStatusIsPublished()
    {
        OpenAPI openAPI = build(DeclaredConflictService.class);

        // a create declares its own 409 next to the default one, and either can be returned
        Schema<?> schema = errorSchema(openAPI, "409");
        assertThat(schema.getOneOf()).isNotNull();
        List<String> alternatives = schema.getOneOf().stream()
                .map(alternative -> ((Schema<?>) alternative).get$ref())
                .toList();
        assertThat(alternatives).containsExactly("#/components/schemas/Conflict", "#/components/schemas/Exists");
        assertThat(onlyPost(openAPI).getResponses().getResponse().get("409").getDescription())
                .isEqualTo("ConflictResponse, ApiAlreadyExists");
    }

    @Test
    public void testResponseWithUniqueStatusIsPublishedAsIs()
    {
        OpenAPI openAPI = build(DefaultConflictService.class);

        Schema<?> schema = errorSchema(openAPI, "409");
        assertThat(schema.getOneOf()).isNull();
        assertThat(schema.get$ref()).isEqualTo("#/components/schemas/Exists");
        assertThat(onlyPost(openAPI).getResponses().getResponse().get("409").getDescription())
                .isEqualTo("ApiAlreadyExists");
    }

    private static OpenAPI build(Class<?> service)
    {
        ModelApi modelApi = apiBuilder().add(service).build();
        assertThat(modelApi.modelServices().errors()).isEmpty();

        ModelServiceType serviceType = modelApi.modelServices().services().iterator().next().service().type();
        OpenAPI openAPI = OpenApiProvider.create(modelApi.modelServices(), new OpenApiMetadata(Optional.empty(), ImmutableList.of(), "/", Duration.ofMinutes(5), OPENAPI_3_0_1), ApiBuilderConfig.jackson())
                .build(serviceType, _ -> true);
        validateOpenApiJson(OPEN_API_CODEC.toJson(openAPI));
        return openAPI;
    }

    private static Schema<?> errorSchema(OpenAPI openAPI, String status)
    {
        return onlyPost(openAPI).getResponses().getResponse().get(status).getContent().getMediaTypes().get("application/json").getSchema();
    }

    private static Operation onlyPost(OpenAPI openAPI)
    {
        List<Operation> posts = openAPI.getPaths().getPaths().values().stream()
                .map(pathItem -> pathItem.getPost())
                .filter(operation -> operation != null)
                .toList();
        assertThat(posts).hasSize(1);
        return posts.getFirst();
    }

    @ApiService(name = "declaredConflict", type = ServiceType.class, description = "Declared conflict operations")
    public static class DeclaredConflictService
    {
        @ApiCreate(description = "Create that declares its own conflict", quotas = "things", responses = ConflictResponse.class)
        public void create() {}
    }

    @ApiService(name = "defaultConflict", type = ServiceType.class, description = "Default conflict operations")
    public static class DefaultConflictService
    {
        @ApiCreate(description = "Create with the default conflict only", quotas = "things")
        public void create() {}
    }

    @ApiResponse(name = "conflict", description = "The request conflicts with the current state", status = CONFLICT)
    public record ConflictResponse(String code, String reason) {}
}
