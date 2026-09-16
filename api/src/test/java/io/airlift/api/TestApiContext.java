package io.airlift.api;

import io.airlift.api.builders.MethodBuilder;
import io.airlift.api.model.ModelMethod;
import io.airlift.api.model.ModelResource;
import io.airlift.api.model.ModelServices;
import io.airlift.api.openapi.OpenApiMetadata;
import io.airlift.api.openapi.OpenApiProvider;
import io.airlift.api.openapi.models.OpenAPI;
import io.airlift.api.openapi.models.Operation;
import io.airlift.api.validation.ValidatorException;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.container.AsyncResponse;
import jakarta.ws.rs.container.Suspended;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.UriInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static io.airlift.api.builders.ApiBuilder.apiBuilder;
import static io.airlift.api.builders.ResourceBuilder.resourceBuilder;
import static io.airlift.api.model.ModelResourceModifier.PATCH;
import static io.airlift.api.openapi.OpenApiMetadata.OpenApiVersion.OPENAPI_3_0_1;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class TestApiContext
{
    @Test
    public void testModelAndValidation()
    {
        ModelServices services = apiBuilder().add(ContextService.class).build().modelServices();
        assertThat(services.errors()).isEmpty();
        assertThat(services.services()).singleElement().satisfies(service -> {
            assertThat(service.methods()).hasSize(4);
            for (ModelMethod method : service.methods()) {
                assertThat(method.parameters()).isEmpty();
                if (method.method().getName().equals("get")) {
                    assertThat(method.requestBody()).isEmpty();
                    assertThat(method.optionalParameters()).isEmpty();
                }
                else {
                    assertThat(method.requestBody()).map(ModelResource::type).contains(Payload.class);
                    assertThat(method.requestBody().orElseThrow().modifiers().contains(PATCH))
                            .isEqualTo(method.method().getName().equals("patch"));
                    assertThat(method.optionalParameters()).singleElement()
                            .satisfies(parameter -> assertThat(parameter.type()).isEqualTo(ApiValidateOnly.class));
                }
            }
        });
    }

    @Test
    public void testOpenApiExcludesContext()
    {
        ModelServices services = apiBuilder().add(ContextService.class).build().modelServices();
        assertThat(services.errors()).isEmpty();
        OpenApiMetadata metadata = new OpenApiMetadata(Optional.empty(), List.of(), "/", Duration.ofMinutes(5), OPENAPI_3_0_1);
        OpenAPI openApi = OpenApiProvider.create(services, metadata, ApiBuilderConfig.jackson())
                .build(services.services().iterator().next().service().type(), _ -> true);

        assertThat(openApi.getPaths().getPaths()).containsOnlyKeys("/public/api/v1/payload", "/public/api/v1/payload:patch");
        assertThat(openApi.getPaths().get("/public/api/v1/payload").getGet().getParameters()).isNullOrEmpty();
        assertThat(openApi.getPaths().get("/public/api/v1/payload").getGet().getRequestBody()).isNull();
        List<Operation> operations = List.of(
                openApi.getPaths().get("/public/api/v1/payload").getPost(),
                openApi.getPaths().get("/public/api/v1/payload").getPut(),
                openApi.getPaths().get("/public/api/v1/payload:patch").getPatch());
        for (Operation operation : operations) {
            assertThat(operation.getParameters()).singleElement()
                    .satisfies(parameter -> assertThat(parameter.getName()).isEqualTo("validateOnly"));
            assertThat(operation.getRequestBody().getContent().getMediaTypes().get("application/json").getSchema().get$ref()).contains("Payload");
        }
        assertThat(openApi.getComponents().getSchemas()).doesNotContainKey("UUID");
    }

    @ParameterizedTest
    @ValueSource(strings = {"unknown", "contextAndApi", "contextAndJaxrs", "contextAndSuspended", "twoContexts", "contextAndUnknown", "contextAndQuery", "jaxrsAndApi", "suspendedAndApi", "jaxrsAndSuspended"})
    public void testInvalidAnnotations(String methodName)
    {
        Method method = Arrays.stream(InvalidService.class.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(methodName))
                .findFirst().orElseThrow();
        assertThatThrownBy(() -> methodBuilder(method).build())
                .isInstanceOf(ValidatorException.class)
                .hasMessageContaining("Invalid annotations on parameter");
    }

    @Test
    public void testContextOnlyAndExistingAnnotations()
            throws Exception
    {
        for (String name : List.of("createContextOnly", "updateContextOnly", "existingContext", "existingSuspended")) {
            Method method = Arrays.stream(OtherService.class.getDeclaredMethods())
                    .filter(candidate -> candidate.getName().equals(name))
                    .findFirst().orElseThrow();
            ModelMethod model = methodBuilder(method).build().orElseThrow();
            assertThat(model.parameters()).isEmpty();
            assertThat(model.optionalParameters()).isEmpty();
            assertThat(model.requestBody()).isEmpty();
        }
        assertThatThrownBy(() -> methodBuilder(OtherService.class.getMethod("twoBodies", UUID.class, Payload.class, Payload.class)).build())
                .isInstanceOf(ValidatorException.class)
                .hasMessageContaining("more than one request body");
    }

    private static MethodBuilder methodBuilder(Method method)
    {
        return MethodBuilder.methodBuilder(method, type -> resourceBuilder(type, ApiBuilderConfig.jackson().enumValueResolver()));
    }

    @ApiContext
    @Target(PARAMETER)
    @Retention(RUNTIME)
    public @interface Injected
    {
        String value() default "request";
    }

    @ApiContext
    @Target(PARAMETER)
    @Retention(RUNTIME)
    public @interface OtherContext {}

    @Target(PARAMETER)
    @Retention(RUNTIME)
    public @interface Unknown {}

    public static class ContextServiceType
            extends ServiceType
    {
        @Override
        public Set<ApiServiceTrait> traits()
        {
            return Set.of();
        }
    }

    @ApiResource(name = "payload", description = "A payload")
    public record Payload(String value) {}

    @ApiService(type = ContextServiceType.class, name = "context", description = "Context parameters")
    public static class ContextService
    {
        @ApiGet(description = "Get context")
        public Payload get(@Injected("X-Request-Id") UUID requestId, @Context UriInfo uriInfo)
        {
            return new Payload(requestId + ":" + uriInfo.getRequestUri().getPath());
        }

        @ApiCreate(description = "Create with context")
        public Payload create(@Injected("X-Request-Id") UUID requestId, Payload payload, @ApiParameter ApiValidateOnly validateOnly)
        {
            return new Payload(requestId + ":" + payload.value() + ":" + validateOnly.requested());
        }

        @ApiUpdate(description = "Update with context")
        public Payload update(Payload payload, @Injected("X-Request-Id") UUID requestId, @ApiParameter ApiValidateOnly validateOnly)
        {
            return new Payload(requestId + ":" + payload.value() + ":" + validateOnly.requested());
        }

        @ApiCustom(type = ApiType.UPDATE, verb = "patch", description = "Patch with context")
        public void patch(@Injected UUID requestId, ApiPatch<Payload> patch, @ApiParameter ApiValidateOnly validateOnly) {}
    }

    @SuppressWarnings("unused")
    public static class OtherService
    {
        @ApiCreate(description = "Test")
        public void createContextOnly(@Injected UUID requestId) {}

        @ApiUpdate(description = "Test")
        public void updateContextOnly(@Injected UUID requestId) {}

        @ApiCreate(description = "Test")
        public void existingContext(@Context UriInfo uriInfo) {}

        @ApiCreate(description = "Test")
        public void existingSuspended(@Suspended AsyncResponse response) {}

        @ApiCreate(description = "Test")
        public void twoBodies(@Injected UUID requestId, Payload first, Payload second) {}
    }

    @SuppressWarnings("unused")
    public static class InvalidService
    {
        @ApiCreate(description = "Test")
        public void unknown(@Unknown UUID value) {}

        @ApiCreate(description = "Test")
        public void contextAndApi(@Injected @ApiParameter ApiValidateOnly value) {}

        @ApiCreate(description = "Test")
        public void contextAndJaxrs(@Injected @Context UUID value) {}

        @ApiCreate(description = "Test")
        public void contextAndSuspended(@Injected @Suspended AsyncResponse value) {}

        @ApiCreate(description = "Test")
        public void twoContexts(@Injected @OtherContext UUID value) {}

        @ApiCreate(description = "Test")
        public void contextAndUnknown(@Injected @Unknown UUID value) {}

        @ApiCreate(description = "Test")
        public void contextAndQuery(@Injected @QueryParam("value") UUID value) {}

        @ApiCreate(description = "Test")
        public void jaxrsAndApi(@Context @ApiParameter ApiValidateOnly value) {}

        @ApiCreate(description = "Test")
        public void suspendedAndApi(@Suspended @ApiParameter AsyncResponse value) {}

        @ApiCreate(description = "Test")
        public void jaxrsAndSuspended(@Context @Suspended AsyncResponse value) {}
    }
}
