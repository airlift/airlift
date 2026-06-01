package io.airlift.api.servertests.openapi;

import com.fasterxml.jackson.annotation.JsonValue;
import com.google.common.collect.ImmutableList;
import com.google.inject.Guice;
import io.airlift.api.ApiCustom;
import io.airlift.api.ApiGet;
import io.airlift.api.ApiPolyResource;
import io.airlift.api.ApiResource;
import io.airlift.api.ApiService;
import io.airlift.api.ApiType;
import io.airlift.api.ApiUnwrapped;
import io.airlift.api.ServiceType;
import io.airlift.api.binding.ApiModule;
import io.airlift.api.builders.ApiBuilder;
import io.airlift.api.model.ModelApi;
import io.airlift.api.model.ModelServiceType;
import io.airlift.api.openapi.OpenApiMetadata;
import io.airlift.api.openapi.OpenApiProvider;
import io.airlift.api.openapi.models.OpenAPI;
import io.airlift.api.openapi.models.Schema;
import io.airlift.json.JsonModule;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.assertj.core.api.Assertions.assertThat;

public class TestJsonValueOpenApi
{
    private static final OpenApiMetadata OPEN_API_METADATA = new OpenApiMetadata(Optional.empty(), ImmutableList.of());

    @Test
    public void testStringJsonValueResourceSchema()
    {
        OpenAPI openAPI = buildOpenApi(JsonValueService.class);

        Schema<?> schema = schema(openAPI, "StringValue");
        assertThat(schema.getType()).isEqualTo("string");
        assertThat(schema.getDescription()).isEqualTo("String value resource");
        assertThat(schema.getProperties()).isNull();
    }

    @Test
    public void testListJsonValueResourceSchema()
    {
        OpenAPI openAPI = buildOpenApi(JsonValueService.class);

        Schema<?> schema = schema(openAPI, "SimpleListValue");
        assertThat(schema.getType()).isEqualTo("array");
        assertThat(schema.getDescription()).isEqualTo("Simple list value resource");
        assertThat(schema.getItems().get$ref()).isEqualTo("#/components/schemas/SimpleValue");
    }

    @Test
    public void testPolyListJsonValueResourceSchema()
    {
        OpenAPI openAPI = buildOpenApi(JsonValueService.class);

        Schema<?> schema = schema(openAPI, "PolyListValue");
        assertThat(schema.getType()).isEqualTo("array");
        assertThat(schema.getItems().get$ref()).isEqualTo("#/components/schemas/JsonValuePoly");
    }

    @Test
    public void testResourceIdentityIsPreservedForPathsAndResponses()
    {
        OpenAPI openAPI = buildOpenApi(JsonValueService.class);

        assertThat(openAPI.getPaths().getPaths()).containsKey("/public/api/v1/kqlQueryResponse:query");
        assertThat(openAPI.getPaths().get("/public/api/v1/kqlQueryResponse:query").getGet().getOperationId()).isEqualTo("queryKqlQueryResponse");
        assertThat(successResponseSchema(openAPI, "/public/api/v1/kqlQueryResponse:query").get$ref()).isEqualTo("#/components/schemas/KqlQueryResponse");

        Schema<?> schema = schema(openAPI, "KqlQueryResponse");
        assertThat(schema.getType()).isEqualTo("array");
        assertThat(schema.getItems().get$ref()).isEqualTo("#/components/schemas/SimpleValue");
    }

    @Test
    public void testOpenApiAlternateNameIsPreserved()
    {
        OpenAPI openAPI = buildOpenApi(JsonValueService.class);

        assertThat(openAPI.getPaths().getPaths()).containsKey("/public/api/v1/alternateStringValue");
        assertThat(successResponseSchema(openAPI, "/public/api/v1/alternateStringValue").get$ref()).isEqualTo("#/components/schemas/AlternateWireValue");

        Schema<?> schema = schema(openAPI, "AlternateWireValue");
        assertThat(schema.getType()).isEqualTo("string");
        assertThat(schema.getDescription()).isEqualTo("Alternate string value resource");
    }

    @Test
    public void testNormalRecordResourceIsUnchanged()
    {
        OpenAPI openAPI = buildOpenApi(JsonValueService.class);

        Schema<?> schema = schema(openAPI, "NormalValue");
        assertThat(schema.getProperties()).containsOnlyKeys("value");
        assertThat(schema.getRequired()).containsExactly("value");
    }

    @Test
    public void testInactiveJsonValueResourceIsStructural()
    {
        OpenAPI openAPI = buildOpenApi(JsonValueService.class);

        Schema<?> schema = schema(openAPI, "InactiveJsonValue");
        assertThat(schema.getProperties()).containsOnlyKeys("value");
        assertThat(schema.getRequired()).containsExactly("value");
    }

    @Test
    public void testMultipleJsonValueComponentsFailValidation()
    {
        assertThat(errors(MultipleJsonValueService.class))
                .anyMatch(error -> error.contains("more than one @JsonValue record component"));
    }

    @Test
    public void testJsonValueWithUnwrappedComponentFailsValidation()
    {
        assertThat(errors(JsonValueWithUnwrappedService.class))
                .anyMatch(error -> error.contains("@JsonValue resources cannot have @ApiUnwrapped components"));
    }

    @Test
    public void testUnsupportedJsonValueTypeFailsValidation()
    {
        assertThat(errors(UnsupportedJsonValueService.class))
                .anyMatch(error -> error.contains("class java.lang.Object value") && error.contains("not a valid resource type"));
    }

    @Test
    public void testSerializationValidation()
    {
        ModelApi modelApi = modelApi(JsonValueService.class);

        Guice.createInjector(ApiModule.builder().addApi(modelApi).build(), new JsonModule());
    }

    private static OpenAPI buildOpenApi(Class<?> serviceClass)
    {
        ModelApi modelApi = modelApi(serviceClass);
        ModelServiceType modelServiceType = modelApi.modelServices().services().stream()
                .findFirst()
                .orElseThrow()
                .service()
                .type();
        return OpenApiProvider.create(modelApi.modelServices(), OPEN_API_METADATA)
                .build(modelServiceType, _ -> true);
    }

    private static ModelApi modelApi(Class<?> serviceClass)
    {
        ModelApi modelApi = ApiBuilder.apiBuilder().add(serviceClass).build();
        assertThat(modelApi.modelServices().errors()).isEmpty();
        return modelApi;
    }

    private static Set<String> errors(Class<?> serviceClass)
    {
        ModelApi modelApi = ApiBuilder.apiBuilder().add(serviceClass).build();
        return modelApi.modelServices().errors();
    }

    private static Schema<?> schema(OpenAPI openAPI, String name)
    {
        return openAPI.getComponents().getSchemas().get(name);
    }

    private static Schema<?> successResponseSchema(OpenAPI openAPI, String path)
    {
        return openAPI.getPaths()
                .get(path)
                .getGet()
                .getResponses()
                .getResponse()
                .get("200")
                .getContent()
                .getMediaTypes()
                .get(APPLICATION_JSON)
                .getSchema();
    }

    @ApiService(type = ServiceType.class, name = "json value", description = "Json value service")
    public static class JsonValueService
    {
        @ApiGet(description = "Get string value")
        public StringValue stringValue()
        {
            return null;
        }

        @ApiGet(description = "Get simple list value")
        public SimpleListValue simpleListValue()
        {
            return null;
        }

        @ApiGet(description = "Get poly list value")
        public PolyListValue polyListValue()
        {
            return null;
        }

        @ApiCustom(verb = "query", type = ApiType.GET, description = "Query values")
        public KqlQueryResponse query()
        {
            return null;
        }

        @ApiGet(description = "Get normal value")
        public NormalValue normalValue()
        {
            return null;
        }

        @ApiGet(description = "Get alternate string value")
        public AlternateStringValue alternateStringValue()
        {
            return null;
        }

        @ApiGet(description = "Get inactive JsonValue")
        public InactiveJsonValue inactiveJsonValue()
        {
            return null;
        }
    }

    @ApiService(type = ServiceType.class, name = "multiple json value", description = "Multiple JsonValue service")
    public static class MultipleJsonValueService
    {
        @ApiGet(description = "Get multiple")
        public MultipleJsonValue multiple()
        {
            return null;
        }
    }

    @ApiService(type = ServiceType.class, name = "json value unwrapped", description = "JsonValue unwrapped service")
    public static class JsonValueWithUnwrappedService
    {
        @ApiGet(description = "Get unwrapped")
        public JsonValueWithUnwrapped unwrapped()
        {
            return null;
        }
    }

    @ApiService(type = ServiceType.class, name = "unsupported json value", description = "Unsupported JsonValue service")
    public static class UnsupportedJsonValueService
    {
        @ApiGet(description = "Get unsupported")
        public UnsupportedJsonValue unsupported()
        {
            return null;
        }
    }

    @ApiResource(name = "stringValue", description = "String value resource")
    public record StringValue(@JsonValue String value) {}

    @ApiResource(name = "simpleListValue", description = "Simple list value resource")
    public record SimpleListValue(@JsonValue List<SimpleValue> values) {}

    @ApiResource(name = "polyListValue", description = "Poly list value resource")
    public record PolyListValue(@JsonValue List<JsonValuePoly> values) {}

    @ApiResource(name = "kqlQueryResponse", description = "KQL query response")
    public record KqlQueryResponse(@JsonValue List<SimpleValue> values) {}

    @ApiResource(name = "normalValue", description = "Normal value resource")
    public record NormalValue(String value) {}

    @ApiResource(name = "alternateStringValue", openApiAlternateName = "alternateWireValue", description = "Alternate string value resource")
    public record AlternateStringValue(@JsonValue String value) {}

    @ApiResource(name = "inactiveJsonValue", description = "Inactive JsonValue resource")
    public record InactiveJsonValue(@JsonValue(false) String value) {}

    @ApiResource(name = "multipleJsonValue", description = "Multiple JsonValue resource")
    public record MultipleJsonValue(@JsonValue String first, @JsonValue int second) {}

    @ApiResource(name = "jsonValueWithUnwrapped", description = "JsonValue with unwrapped resource")
    public record JsonValueWithUnwrapped(@JsonValue String value, @ApiUnwrapped UnwrappedValue unwrapped) {}

    @ApiResource(name = "unsupportedJsonValue", description = "Unsupported JsonValue resource")
    public record UnsupportedJsonValue(@JsonValue Object value) {}

    @ApiResource(name = "simpleValue", description = "Simple value resource")
    public record SimpleValue(String name) {}

    @ApiResource(name = "unwrappedValue", description = "Unwrapped value resource")
    public record UnwrappedValue(String unwrapped) {}

    @ApiPolyResource(name = "jsonValuePoly", description = "JsonValue poly resource", key = "type")
    public sealed interface JsonValuePoly
            permits PolyOne, PolyTwo {}

    @ApiResource(name = "polyOne", description = "Poly one resource")
    public record PolyOne(String one)
            implements JsonValuePoly {}

    @ApiResource(name = "polyTwo", description = "Poly two resource")
    public record PolyTwo(String two)
            implements JsonValuePoly {}
}
