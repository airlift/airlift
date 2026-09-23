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
import java.util.Optional;

import static io.airlift.api.builders.ApiBuilder.apiBuilder;
import static io.airlift.api.openapi.OpenApiMetadata.OpenApiVersion.OPENAPI_3_0_1;
import static io.airlift.api.servertests.openapi.TestOpenApi.validateOpenApiJson;
import static io.airlift.json.JsonCodec.jsonCodec;
import static org.assertj.core.api.Assertions.assertThat;

public class TestOpenApiHeaderNames
{
    private static final JsonCodec<OpenAPI> OPEN_API_CODEC = jsonCodec(OpenAPI.class);

    @Test
    public void testExplicitAndDerivedHeaderNamesArePublished()
    {
        ModelApi modelApi = apiBuilder().add(HeaderService.class).build();
        assertThat(modelApi.modelServices().errors()).isEmpty();

        OpenAPI openAPI = build(modelApi);
        validateOpenApiJson(OPEN_API_CODEC.toJson(openAPI));

        List<String> headerNames = onlyPost(openAPI).getParameters().stream()
                .filter(parameter -> "header".equals(parameter.getIn()))
                .map(Parameter::getName)
                .toList();
        // an explicitly named header keeps its name; an unnamed one keeps the derived X- name
        assertThat(headerNames).containsExactlyInAnyOrder("Idempotency-Key", "X-REQUEST-ID");
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
                .map(pathItem -> pathItem.getPost())
                .filter(operation -> operation != null)
                .toList();
        assertThat(posts).hasSize(1);
        return posts.getFirst();
    }

    @ApiService(name = "headerNames", type = ServiceType.class, description = "Header name operations")
    public static class HeaderService
    {
        @ApiCreate(description = "Create with named and unnamed headers", quotas = "things")
        public void create(@ApiParameter(name = "Idempotency-Key") ApiHeader idempotencyKey, @ApiParameter ApiHeader requestId) {}
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
