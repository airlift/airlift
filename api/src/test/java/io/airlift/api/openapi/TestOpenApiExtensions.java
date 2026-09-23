package io.airlift.api.openapi;

import com.google.common.collect.ImmutableList;
import io.airlift.api.ApiBuilderConfig;
import io.airlift.api.ApiCreate;
import io.airlift.api.ApiHeader;
import io.airlift.api.ApiParameter;
import io.airlift.api.ApiService;
import io.airlift.api.ServiceType;
import io.airlift.api.model.ModelApi;
import io.airlift.api.model.ModelServiceType;
import io.airlift.api.openapi.models.OpenAPI;
import io.airlift.api.openapi.models.Operation;
import io.airlift.api.openapi.models.Parameter;
import io.airlift.json.JsonCodec;
import org.junit.jupiter.api.Test;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static io.airlift.api.builders.ApiBuilder.apiBuilder;
import static io.airlift.api.openapi.OpenApiMetadata.OpenApiVersion.OPENAPI_3_0_1;
import static io.airlift.api.servertests.openapi.TestOpenApi.validateOpenApiJson;
import static io.airlift.json.JsonCodec.jsonCodec;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class TestOpenApiExtensions
{
    private static final JsonCodec<OpenAPI> OPEN_API_CODEC = jsonCodec(OpenAPI.class);

    @Test
    public void testOperationHasNoExtensionsByDefault()
    {
        OpenAPI openAPI = build(ImmutableList.of());
        validateOpenApiJson(OPEN_API_CODEC.toJson(openAPI));

        assertThat(onlyPost(openAPI).getExtensions()).isEmpty();
    }

    @Test
    public void testEveryFilterIsApplied()
    {
        OpenAPI openAPI = build(ImmutableList.of(extensionFilter("x-first", "one"), extensionFilter("x-second", "two")));
        validateOpenApiJson(OPEN_API_CODEC.toJson(openAPI));

        assertThat(onlyPost(openAPI).getExtensions())
                .containsEntry("x-first", "one")
                .containsEntry("x-second", "two");
    }

    @Test
    public void testFilterSeesTheBuiltOperation()
    {
        OpenAPI openAPI = build(ImmutableList.of((_, _, operation) -> {
            operation.addExtension("x-operation-id", operation.getOperationId());
            return operation;
        }));

        assertThat(onlyPost(openAPI).getExtensions()).containsEntry("x-operation-id", "create");
    }

    @Test
    public void testFiltersCannotClaimTheSameExtension()
    {
        assertThatThrownBy(() -> build(ImmutableList.of(extensionFilter("x-shared", "one"), extensionFilter("x-shared", "two"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("OpenAPI extension 'x-shared' is claimed by more than one extension filter");
    }

    @Test
    public void testExtensionNameMustBePrefixed()
    {
        assertThatThrownBy(() -> build(ImmutableList.of(extensionFilter("unprefixed", "value"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("OpenAPI extension name must start with \"x-\": unprefixed");
    }

    @Test
    public void testIdempotencyKeyExample()
    {
        OpenAPI openAPI = build(IdempotentService.class, ImmutableList.of(IDEMPOTENCY_FILTER));
        validateOpenApiJson(OPEN_API_CODEC.toJson(openAPI));

        assertThat(onlyPost(openAPI).getExtensions()).containsEntry("x-airlift-idempotency", Map.of("header", "Idempotency-Key"));
    }

    @Test
    public void testIdempotencyKeyExampleRejectsAnUnknownHeader()
    {
        assertThatThrownBy(() -> build(MissingHeaderService.class, ImmutableList.of(IDEMPOTENCY_FILTER)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("idempotency key header 'Idempotency-Key' is not an operation header parameter");
    }

    private static OpenApiExtensionFilter extensionFilter(String name, Object value)
    {
        return (_, _, operation) -> {
            operation.addExtension(name, value);
            return operation;
        };
    }

    private static OpenAPI build(List<OpenApiExtensionFilter> extensionFilters)
    {
        return build(ThingService.class, extensionFilters);
    }

    private static OpenAPI build(Class<?> serviceClass, List<OpenApiExtensionFilter> extensionFilters)
    {
        ModelApi modelApi = apiBuilder().add(serviceClass).build();
        assertThat(modelApi.modelServices().errors()).isEmpty();

        ModelServiceType serviceType = modelApi.modelServices().services().iterator().next().service().type();
        OpenApiMetadata metadata = new OpenApiMetadata(Optional.empty(), ImmutableList.of(), "/", Duration.ofMinutes(5), OPENAPI_3_0_1);
        return OpenApiProvider.create(modelApi.modelServices(), metadata, extensionFilters, ApiBuilderConfig.jackson())
                .build(serviceType, _ -> true);
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

    @ApiService(name = "thing", type = ServiceType.class, description = "Thing operations")
    public static class ThingService
    {
        @ApiCreate(description = "Create a thing", quotas = "things")
        public void create() {}
    }

    /**
     * An application declares its own idempotency annotation and publishes it with its own extension filter.
     */
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface IdempotencyKey
    {
        String header();
    }

    private static final OpenApiExtensionFilter IDEMPOTENCY_FILTER = (_, modelMethod, operation) -> {
        IdempotencyKey idempotencyKey = modelMethod.method().getAnnotation(IdempotencyKey.class);
        if (idempotencyKey == null) {
            return operation;
        }

        String headerName = idempotencyKey.header();
        Parameter headerParameter = operation.getParameters().stream()
                .filter(parameter -> "header".equals(parameter.getIn()))
                .filter(parameter -> headerName.equalsIgnoreCase(parameter.getName()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("idempotency key header '%s' is not an operation header parameter".formatted(headerName)));

        operation.addExtension("x-airlift-idempotency", Map.of("header", headerParameter.getName()));
        return operation;
    };

    @ApiService(name = "idempotent", type = ServiceType.class, description = "Idempotent operations")
    public static class IdempotentService
    {
        @ApiCreate(description = "Create with an idempotency key", quotas = "things")
        @IdempotencyKey(header = "Idempotency-Key")
        public void create(@ApiParameter(name = "Idempotency-Key") ApiHeader idempotencyKey) {}
    }

    @ApiService(name = "missingHeader", type = ServiceType.class, description = "Invalid idempotent operations")
    public static class MissingHeaderService
    {
        @ApiCreate(description = "Create without the declared header", quotas = "things")
        @IdempotencyKey(header = "Idempotency-Key")
        public void create() {}
    }
}
