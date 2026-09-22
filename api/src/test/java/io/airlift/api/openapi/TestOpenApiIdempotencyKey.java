package io.airlift.api.openapi;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.google.common.collect.ImmutableList;
import io.airlift.api.ApiBuilderConfig;
import io.airlift.api.ApiCreate;
import io.airlift.api.ApiDescription;
import io.airlift.api.ApiGet;
import io.airlift.api.ApiHeader;
import io.airlift.api.ApiId;
import io.airlift.api.ApiParameter;
import io.airlift.api.ApiResource;
import io.airlift.api.ApiResourceVersion;
import io.airlift.api.ApiResponseHeaders;
import io.airlift.api.ApiService;
import io.airlift.api.ServiceType;
import io.airlift.api.TestId;
import io.airlift.api.model.ModelApi;
import io.airlift.api.model.ModelServiceType;
import io.airlift.api.openapi.models.OpenAPI;
import io.airlift.api.openapi.models.Operation;
import io.airlift.api.openapi.models.Parameter;
import io.airlift.json.JsonCodec;
import org.junit.jupiter.api.Test;

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

public class TestOpenApiIdempotencyKey
{
    private static final JsonCodec<OpenAPI> OPEN_API_CODEC = jsonCodec(OpenAPI.class);

    @Test
    public void testIdempotencyHeaderIsPublished()
    {
        ModelApi modelApi = apiBuilder().add(IdempotentService.class).build();
        assertThat(modelApi.modelServices().errors()).isEmpty();

        OpenAPI openAPI = build(modelApi);
        validateOpenApiJson(OPEN_API_CODEC.toJson(openAPI));

        Operation create = onlyPost(openAPI);
        List<String> headerNames = create.getParameters().stream()
                .filter(parameter -> "header".equals(parameter.getIn()))
                .map(Parameter::getName)
                .toList();
        // an explicitly named header keeps its name; an unnamed one keeps the derived X- name
        assertThat(headerNames).containsExactlyInAnyOrder("Idempotency-Key", "X-REQUEST-ID");
        assertThat(create.getExtensions()).containsEntry("x-airlift-idempotency", Map.of("header", "Idempotency-Key"));
    }

    @Test
    public void testOperationWithoutIdempotencyKeyHasNoExtension()
    {
        ModelApi modelApi = apiBuilder().add(PlainService.class).build();
        assertThat(modelApi.modelServices().errors()).isEmpty();

        OpenAPI openAPI = build(modelApi);
        onlyPost(openAPI);
        assertThat(OPEN_API_CODEC.toJson(openAPI)).doesNotContain("x-airlift-idempotency");
    }

    @Test
    public void testIdempotencyKeyRequiresMatchingHeaderParameter()
    {
        ModelApi modelApi = apiBuilder().add(MissingHeaderService.class).build();
        assertThat(modelApi.modelServices().errors()).isEmpty();

        assertThatThrownBy(() -> build(modelApi))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("@OpenApiIdempotencyKey header 'Idempotency-Key' is not an operation header parameter");
    }

    @Test
    public void testHeaderNameMustBeHttpToken()
    {
        ModelApi modelApi = apiBuilder().add(InvalidHeaderNameService.class).build();
        assertThat(modelApi.modelServices().errors())
                .anyMatch(error -> error.contains("@ApiParameter name is not a valid HTTP header name: Bad Header"));
    }

    @Test
    public void testExplicitNameIsOnlyAllowedOnHeaders()
    {
        // ids and response headers take their own validation paths, and must reject the name there too
        assertThat(apiBuilder().add(NamedIdService.class).build().modelServices().errors())
                .anyMatch(error -> error.contains("@ApiParameter name is only supported for ApiHeader parameters"));
        assertThat(apiBuilder().add(NamedResponseHeadersService.class).build().modelServices().errors())
                .anyMatch(error -> error.contains("@ApiParameter name is only supported for ApiHeader parameters"));
    }

    private static OpenAPI build(ModelApi modelApi)
    {
        ModelServiceType serviceType = modelApi.modelServices().services().iterator().next().service().type();
        return OpenApiProvider.create(modelApi.modelServices(), new OpenApiMetadata(Optional.empty(), ImmutableList.of(), "/", Duration.ofMinutes(5), OPENAPI_3_0_1), ApiBuilderConfig.jackson())
                .build(serviceType, _ -> true);
    }

    private static Operation onlyPost(OpenAPI openAPI)
    {
        List<Operation> posts = openAPI.getPaths().getPaths().values().stream()
                .map(path -> path.getPost())
                .filter(operation -> operation != null)
                .toList();
        assertThat(posts).hasSize(1);
        return posts.getFirst();
    }

    @ApiService(name = "idempotent", type = ServiceType.class, description = "Idempotent operations")
    public static class IdempotentService
    {
        @ApiCreate(description = "Create with an idempotency key", quotas = "things")
        @OpenApiIdempotencyKey(header = "Idempotency-Key")
        public void create(@ApiParameter(name = "Idempotency-Key") ApiHeader idempotencyKey, @ApiParameter ApiHeader requestId) {}
    }

    @ApiService(name = "plain", type = ServiceType.class, description = "Plain operations")
    public static class PlainService
    {
        @ApiCreate(description = "Create without an idempotency key", quotas = "things")
        public void create(@ApiParameter ApiHeader requestId) {}
    }

    @ApiService(name = "missingHeader", type = ServiceType.class, description = "Invalid idempotent operations")
    public static class MissingHeaderService
    {
        @ApiCreate(description = "Create without the declared header", quotas = "things")
        @OpenApiIdempotencyKey(header = "Idempotency-Key")
        public void create() {}
    }

    @ApiService(name = "namedId", type = ServiceType.class, description = "Named id operations")
    public static class NamedIdService
    {
        @ApiGet(description = "Get with a named id")
        public NamedResource get(@ApiParameter(name = "X-Id") NamedResourceId id)
        {
            return null;
        }
    }

    @ApiService(name = "namedResponseHeaders", type = ServiceType.class, description = "Named response headers operations")
    public static class NamedResponseHeadersService
    {
        @ApiCreate(description = "Create with named response headers", quotas = "things")
        public void create(@ApiParameter(name = "X-Response") ApiResponseHeaders responseHeaders) {}
    }

    @ApiResource(name = "namedResource", description = "Named resource")
    public record NamedResource(ApiResourceVersion syncToken, @ApiDescription("the id") NamedResourceId namedResourceId) {}

    public static class NamedResourceId
            extends ApiId<NamedResource, TestId>
    {
        public NamedResourceId()
        {
            this("dummy");
        }

        public NamedResourceId(TestId internalId)
        {
            super(internalId);
        }

        @JsonCreator
        public NamedResourceId(String id)
        {
            super(id);
        }
    }

    @ApiService(name = "invalidHeaderName", type = ServiceType.class, description = "Invalid header name operations")
    public static class InvalidHeaderNameService
    {
        @ApiCreate(description = "Create with an invalid header name", quotas = "things")
        public void create(@ApiParameter(name = "Bad Header") ApiHeader badHeader) {}
    }
}
