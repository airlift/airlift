package io.airlift.api.validation;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import io.airlift.api.ApiBuilderConfig;
import io.airlift.api.ApiCreate;
import io.airlift.api.ApiDescription;
import io.airlift.api.ApiEnumNamingFormat;
import io.airlift.api.ApiEnumValueResolver;
import io.airlift.api.ApiFilter;
import io.airlift.api.ApiGet;
import io.airlift.api.ApiJsonNode;
import io.airlift.api.ApiList;
import io.airlift.api.ApiOrderBy;
import io.airlift.api.ApiParameter;
import io.airlift.api.ApiPolyResource;
import io.airlift.api.ApiResource;
import io.airlift.api.ApiService;
import io.airlift.api.ApiServiceTrait;
import io.airlift.api.ApiServiceType;
import io.airlift.api.ApiStringId;
import io.airlift.api.ServiceType;
import io.airlift.api.binding.ApiModule;
import io.airlift.api.binding.PolyResourceModule;
import io.airlift.api.builders.ApiBuilder;
import io.airlift.api.builders.ResourceBuilder;
import io.airlift.api.model.ModelApi;
import io.airlift.api.model.ModelResource;
import io.airlift.api.model.ModelResourceType;
import io.airlift.api.model.ModelServiceMetadata;
import io.airlift.api.model.ModelServiceType;
import io.airlift.api.model.ModelServices;
import io.airlift.api.openapi.OpenApiMetadata;
import io.airlift.api.openapi.OpenApiProvider;
import io.airlift.api.openapi.models.OpenAPI;
import io.airlift.api.openapi.models.Schema;
import io.airlift.api.responses.ApiBadRequest;
import io.airlift.json.JsonModule;
import jakarta.ws.rs.WebApplicationException;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static io.airlift.api.ApiEnumNamingFormat.PASCAL_CASE;
import static io.airlift.api.internals.Mappers.buildHeaderName;
import static io.airlift.api.model.ModelResourceModifier.OPTIONAL;
import static io.airlift.api.model.ModelResourceModifier.RECURSIVE_REFERENCE;
import static io.airlift.api.model.ModelResourceType.BASIC;
import static io.airlift.api.model.ModelResourceType.LIST;
import static io.airlift.api.validation.ResourceValidator.validateResource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class TestConforming
{
    private static final ApiBuilderConfig CONFIG = ApiBuilderConfig.jackson();
    private static final ModelServiceMetadata METADATA = new ModelServiceMetadata("dummy", new ModelServiceType("dummy", 1, "dummy", "dummy", ImmutableSet.copyOf(ApiServiceTrait.values()), PASCAL_CASE), "dummy", ImmutableList.of());

    @Test
    public void testDuplicateMethods()
    {
        ModelServices services = ApiBuilder.apiBuilder().add(ServiceWithDuplicateMethods.class).build().modelServices();
        assertThat(services.errors()).hasSize(1).first().matches(s -> s.contains("Service with duplicate URI"));
    }

    @Test
    public void testDuplicateSameUriDifferentHttp()
    {
        ModelServices services = ApiBuilder.apiBuilder().add(ServiceWithDuplicateMethodsDifferentHttp.class).build().modelServices();
        assertThat(services.errors()).isEmpty();
    }

    @Test
    public void testBadVersionName()
    {
        ModelServices services = ApiBuilder.apiBuilder().add(ServiceWithBadVersion.class).build().modelServices();
        assertThat(services.errors()).anyMatch(s -> s.contains("ApiResourceVersion fields must be named"));
    }

    @Test
    @SuppressWarnings("deprecation")
    public void testDeprecatedEnumNameTypeValidation()
    {
        ValidationContext validationContext = new ValidationContext();
        validationContext.validateName("OrderStatus", ValidationContext.NameType.ENUM);

        assertThatThrownBy(() -> validationContext.validateName("orderStatus", ValidationContext.NameType.ENUM))
                .isInstanceOf(ValidatorException.class)
                .hasMessageContaining("\"orderStatus\" is not a valid enum name for format PascalCase");
    }

    @Test
    public void testNoVersion()
    {
        ModelServices services = ApiBuilder.apiBuilder().add(ServiceWithNoVersion.class).build().modelServices();
        assertThat(services.errors()).hasSize(1).first().matches(s -> s.contains("missing an ApiResourceVersion"));
    }

    @Test
    public void testReadOnlyResource()
    {
        ModelServices services = ApiBuilder.apiBuilder().add(ServiceWithReadOnlyResources.class).build().modelServices();
        assertThat(services.errors()).isEmpty();
    }

    @Test
    public void testDisallowedBothFieldMaskAndPatch()
    {
        ModelServices services = ApiBuilder.apiBuilder().add(ServicePatchWithPatchAndPatch.class).build().modelServices();
        assertThat(services.errors()).withFailMessage(() -> services.errors().toString()).hasSize(1).first().matches(s -> s.contains("has more than one request body parameter"));
    }

    @Test
    public void testBadPatch()
    {
        ModelServices services = ApiBuilder.apiBuilder().add(ServiceWithBadPatch.class).build().modelServices();
        assertThat(services.errors()).hasSize(1).first().matches(s -> s.contains("not a valid resource type"));
    }

    @Test
    public void testValidCreate()
    {
        ModelServices services = ApiBuilder.apiBuilder().add(FoodServiceValid.class).build().modelServices();
        assertThat(services.errors()).isEmpty();
    }

    @Test
    public void testInvalidCreate()
    {
        ModelServices services = ApiBuilder.apiBuilder().add(FoodServiceInvalid.class).build().modelServices();
        assertThat(services.errors()).hasSize(1).first().matches(s -> s.contains("resource parameter has an ApiResourceVersion"));
    }

    @Test
    public void testValidDocumentationLinks()
    {
        ModelServices services = ApiBuilder.apiBuilder().add(ServiceWithValidLinks.class).build().modelServices();
        assertThat(services.errors()).isEmpty();
    }

    @Test
    public void testDocumentationLinksInvalid()
    {
        ModelServices services = ApiBuilder.apiBuilder().add(ServiceWithNoUriLinks.class).build().modelServices();
        assertThat(services.errors()).hasSize(1).first().matches(s -> s.contains("Illegal character") && s.contains("starburst-galaxy-link1-invalid-[]"));
    }

    @Test
    public void testHeaderNames()
    {
        assertThat(buildHeaderName("foo")).isEqualTo("X-FOO");
        assertThat(buildHeaderName("fooBar")).isEqualTo("X-FOO-BAR");
        assertThat(buildHeaderName("Bar")).isEqualTo("X-BAR");
        assertThat(buildHeaderName("Bar")).isEqualTo("X-BAR");
        assertThat(buildHeaderName("Bim_Bam_Boom")).isEqualTo("X-BIM-BAM-BOOM");
    }

    @Test
    public void testBoxedOptionals()
    {
        ValidationContext validationContext = new ValidationContext();
        ModelResource modelResource = resourceBuilder(BoxedOptionals.class).build();
        validateResource(validationContext, METADATA, modelResource, CONFIG.enumValueResolver());

        assertThat(validationContext.errors()).isEmpty();
        assertThat(modelResource.components().get(0).type()).isEqualTo(boolean.class);
        assertThat(modelResource.components().get(1).type()).isEqualTo(int.class);
        assertThat(modelResource.components().get(2).type()).isEqualTo(long.class);
        assertThat(modelResource.components().get(3).type()).isEqualTo(double.class);
    }

    @Test
    public void testAllowAcceptableCollections()
    {
        ModelServices services = ApiBuilder.apiBuilder().add(ServiceWithAcceptableLists.class).build().modelServices();
        assertThat(services.errors()).hasSize(0);
    }

    @Test
    public void testAllTypes()
    {
        // do a thorough test by creating a real ApiModule. This will do full validation including serialization validation
        ModelApi modelApi = ApiBuilder.apiBuilder().add(ServiceWithAllTypes.class).build();
        Module module = ApiModule.builder().addApi(modelApi).build();
        Guice.createInjector(module, new JsonModule());

        ModelResource allTypes = resourceBuilder(ResourceWithAllTypes.class).build();
        assertIdComponent(allTypes, "nameId", ResourceWithAllTypes.SimpleId.class, BASIC, false);
        assertIdComponent(allTypes, "listOfNameIds", ResourceWithAllTypes.SimpleId.class, LIST, false);
        assertIdComponent(allTypes, "maybeNameIds", ResourceWithAllTypes.SimpleId.class, LIST, true);
        assertIdComponent(allTypes, "maybeNameId", ResourceWithAllTypes.SimpleId.class, BASIC, true);
        assertIdComponent(allTypes, "uuidId", ResourceWithAllTypes.SimpleUuidId.class, BASIC, false);
        assertIdComponent(allTypes, "listOfUuidIds", ResourceWithAllTypes.SimpleUuidId.class, LIST, false);
        assertIdComponent(allTypes, "maybeUuidIds", ResourceWithAllTypes.SimpleUuidId.class, LIST, true);
        assertIdComponent(allTypes, "maybeUuidId", ResourceWithAllTypes.SimpleUuidId.class, BASIC, true);
    }

    @Test
    public void testEnumNamingFormatValidation()
    {
        assertThat(ApiBuilder.apiBuilder().add(PascalCaseEnumService.class).build().modelServices().errors()).isEmpty();

        assertThat(ApiBuilder.apiBuilder().add(UpperSnakeCaseEnumService.class).build().modelServices().errors()).isEmpty();

        assertThat(ApiBuilder.apiBuilder().add(DefaultUpperSnakeCaseEnumService.class).build().modelServices().errors())
                .anyMatch(error -> error.contains("SMALL_VALUE") && error.contains("PascalCase"));

        assertThat(ApiBuilder.apiBuilder().add(InvalidEnumService.class).build().modelServices().errors())
                .anyMatch(error -> error.contains("small") && error.contains("PascalCase"));
    }

    @Test
    public void testJsonValueEnumValidation()
    {
        assertThat(ApiBuilder.apiBuilder().add(JsonValueToStringEnumService.class).build().modelServices().errors()).isEmpty();

        assertThat(ApiBuilder.apiBuilder().add(CustomJsonValueEnumService.class).build().modelServices().errors()).isEmpty();

        assertThat(ApiBuilder.apiBuilder().add(NonStringJsonValueEnumService.class).build().modelServices().errors())
                .anyMatch(error -> error.contains("must return a String"));

        assertThat(ApiBuilder.apiBuilder().add(NullJsonValueEnumService.class).build().modelServices().errors())
                .anyMatch(error -> error.contains("returned null"));

        assertThat(ApiBuilder.apiBuilder().add(DuplicateJsonValueEnumService.class).build().modelServices().errors())
                .anyMatch(error -> error.contains("Duplicate enum wire value"));

        assertThat(ApiBuilder.apiBuilder().add(MultipleJsonValueEnumService.class).build().modelServices().errors())
                .anyMatch(error -> error.contains("Multiple @JsonValue methods"));

        assertThat(ApiBuilder.apiBuilder().add(StaticJsonValueEnumService.class).build().modelServices().errors())
                .anyMatch(error -> error.contains("is not an instance method"));
    }

    @Test
    public void testOpenApiEnumValuesUseResolvedWireValues()
    {
        assertThat(openApiEnumValues(PascalCaseEnumService.class, "PascalCaseEnumResource", "value"))
                .containsExactly("Small", "Large");

        assertThat(openApiEnumValues(UpperSnakeCaseEnumService.class, "UpperSnakeCaseEnumResource", "value"))
                .containsExactly("SMALL_VALUE", "LARGE_VALUE");

        assertThat(openApiEnumValues(CustomJsonValueEnumService.class, "CustomJsonValueEnumResource", "value"))
                .containsExactly("SmallWireValue", "LargeWireValue");
        assertThat(openApiProperty(CustomJsonValueEnumService.class, "CustomJsonValueEnumResource", "value").getDescription())
                .contains("\"SmallWireValue\"")
                .contains("\"LargeWireValue\"");
    }

    @Test
    public void testConfiguredEnumValueResolver()
    {
        ApiBuilderConfig config = ApiBuilderConfig.of(new ConfiguredEnumValueResolver());

        ModelApi modelApi = ApiBuilder.apiBuilder(config).add(PascalCaseEnumService.class).build();
        assertThat(modelApi.modelServices().errors()).isEmpty();

        assertThat(openApiEnumValues(PascalCaseEnumService.class, "PascalCaseEnumResource", "value", config))
                .containsExactly("ConfiguredSmall", "ConfiguredLarge");
    }

    @Test
    public void testApiModuleAppliesConfiguredEnumValueResolverAtBuild()
    {
        ApiBuilderConfig config = ApiBuilderConfig.of(new ConfiguredEnumValueResolver());

        Module module = ApiModule.builder()
                .addApi(api -> api.add(DefaultUpperSnakeCaseEnumService.class))
                .withApiBuilderConfig(config)
                .build();

        Guice.createInjector(module, new JsonModule());
    }

    @Test
    public void testApiUuidId()
    {
        UUID uuid = UUID.fromString("00000000-0000-0000-0000-000000000001");
        ResourceWithAllTypes.SimpleUuidId id = new ResourceWithAllTypes.SimpleUuidId(uuid);

        assertThat(id).hasToString(uuid.toString());
        assertThat(id.value()).isEqualTo(uuid);
        assertThat(id.toInternal()).isEqualTo(uuid);
        assertThat(new ResourceWithAllTypes.SimpleUuidId().toInternal()).isEqualTo(new UUID(0L, 0L));
        assertThat(new ResourceWithAllTypes.SimpleUuidId(uuid.toString()).toInternal()).isEqualTo(uuid);
        assertThatThrownBy(() -> new ResourceWithAllTypes.SimpleUuidId((UUID) null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("id is null");
        assertThatThrownBy(() -> new ResourceWithAllTypes.SimpleUuidId("invalid").toInternal())
                .isInstanceOfSatisfying(WebApplicationException.class, exception -> assertThat(exception.getResponse().getEntity())
                        .isEqualTo(new ApiBadRequest(Optional.of("Invalid id: invalid"), Optional.of(ImmutableList.of("id")))));
    }

    @Test
    public void testAllowedObjectContainers()
    {
        // service whose type carries ALLOW_OBJECT_ELEMENTS may declare List<Object>, nested List<Object>, and Map<String, Object> fields
        ModelApi modelApi = ApiBuilder.apiBuilder().add(ServiceWithObjectField.class).build();
        assertThat(modelApi.modelServices().errors()).isEmpty();
        Module module = ApiModule.builder().addApi(modelApi).build();
        Guice.createInjector(module, new JsonModule());
    }

    @Test
    public void testObjectListWithoutTrait()
    {
        ModelServices services = ApiBuilder.apiBuilder().add(ServiceWithObjectListFieldNoTrait.class).build().modelServices();
        assertThat(services.errors()).anyMatch(s -> s.contains("java.util.List<java.lang.Object> requires service trait ALLOW_OBJECT_ELEMENTS"));
    }

    @Test
    public void testObjectMapWithoutTrait()
    {
        ModelServices services = ApiBuilder.apiBuilder().add(ServiceWithObjectMapFieldNoTrait.class).build().modelServices();
        assertThat(services.errors()).anyMatch(s -> s.contains("java.util.Map<java.lang.String, java.lang.Object> requires service trait ALLOW_OBJECT_ELEMENTS"));
    }

    @Test
    public void testJsonNodeIdResourceDisallowed()
    {
        ModelServices services = ApiBuilder.apiBuilder().add(ServiceWithJsonNodeIdParameter.class).build().modelServices();
        assertThat(services.errors()).anyMatch(error -> error.contains("ApiJson types cannot be used as API path parameters"));
    }

    @Test
    public void testResourceWithJsonNodeFieldCanHaveIdParameter()
    {
        ModelServices services = ApiBuilder.apiBuilder().add(ServiceWithJsonNodeFieldIdParameter.class).build().modelServices();
        assertThat(services.errors()).isEmpty();
    }

    @Test
    public void testJsonNodeIdFieldDisallowed()
    {
        ValidationContext validationContext = new ValidationContext();
        validateResource(validationContext, METADATA, resourceBuilder(ResourceWithJsonNodeIdField.class).build(), CONFIG.enumValueResolver());
        assertThat(validationContext.errors()).anyMatch(error -> error.contains("ApiJson types cannot be used as API ID fields"));
    }

    @Test
    public void testJsonNodeFilterFieldDisallowed()
    {
        ModelServices services = ApiBuilder.apiBuilder().add(ServiceWithJsonNodeFilter.class).build().modelServices();
        assertThat(services.errors()).anyMatch(error -> error.contains("ApiJson field json cannot be used as an API query parameter"));
    }

    @Test
    public void testJsonNodeOrderByFieldDisallowed()
    {
        ModelServices services = ApiBuilder.apiBuilder().add(ServiceWithJsonNodeOrderBy.class).build().modelServices();
        assertThat(services.errors()).anyMatch(error -> error.contains("ApiJson field json cannot be used as an API query parameter"));
    }

    @Test
    public void testJsonNodeFilterFieldInPolyResourceDisallowed()
    {
        ModelServices services = ApiBuilder.apiBuilder().add(ServiceWithPolyJsonNodeFilter.class).build().modelServices();
        assertThat(services.errors()).anyMatch(error -> error.contains("ApiJson field json cannot be used as an API query parameter"));
    }

    @Test
    public void testRawJacksonNodeFieldsDisallowed()
    {
        assertJacksonNodeResourceTypeDisallowed(ResourceWithJacksonJsonNodeField.class);
        assertJacksonNodeResourceTypeDisallowed(ResourceWithObjectNodeField.class);
        assertJacksonNodeResourceTypeDisallowed(ResourceWithArrayNodeField.class);
        assertJacksonNodeResourceTypeDisallowed(ResourceWithTextNodeField.class);
        assertJacksonNodeResourceTypeDisallowed(ResourceWithIntNodeField.class);
        assertJacksonNodeResourceTypeDisallowed(ResourceWithNullNodeField.class);
    }

    private static void assertJacksonNodeResourceTypeDisallowed(Class<?> resourceType)
    {
        assertThatThrownBy(() -> resourceBuilder(resourceType).build())
                .isInstanceOf(ValidatorException.class)
                .hasMessageContaining("not a valid resource type");
    }

    @Test
    public void testBadLookup()
    {
        ModelApi modelApi = ApiBuilder.apiBuilder().add(ServiceWithBadLookup.class).build();
        Module module = ApiModule.builder().addApi(modelApi).build();
        assertThatThrownBy(() -> Guice.createInjector(module)).hasMessageContaining("There are Ids used in service methods that are annotated with ApiIdSupportsLookup but missing a binding of ApiIdLookup");
    }

    @ApiService(type = ServiceType.class, name = "json node id service", description = "json node id")
    public static class ServiceWithJsonNodeIdParameter
    {
        @ApiGet(description = "json node id")
        public ResourceWithAllTypes get(@ApiParameter JsonNodeId id)
        {
            return null;
        }
    }

    public static class JsonNodeId
            extends ApiStringId<ApiJsonNode>
    {
        @JsonCreator
        public JsonNodeId(String id)
        {
            super(id);
        }
    }

    @ApiService(type = ServiceType.class, name = "json node field id service", description = "json node field id")
    public static class ServiceWithJsonNodeFieldIdParameter
    {
        @ApiGet(description = "json node field id")
        public ResourceWithJsonNodeFieldWithId get(@ApiParameter JsonNodeFieldResourceId id)
        {
            return null;
        }
    }

    public static class JsonNodeFieldResourceId
            extends ApiStringId<ResourceWithJsonNodeFieldWithId>
    {
        @JsonCreator
        public JsonNodeFieldResourceId(String id)
        {
            super(id);
        }
    }

    @ApiResource(name = "jsonNodeFieldWithId", description = "json node field with id")
    public record ResourceWithJsonNodeFieldWithId(
            @ApiDescription("id") JsonNodeFieldResourceId jsonNodeFieldWithIdId,
            @ApiDescription("json") ApiJsonNode json) {}

    @ApiResource(name = "jsonNodeIdField", description = "json node id field")
    public record ResourceWithJsonNodeIdField(@ApiDescription("id") JsonNodeId jsonId) {}

    @ApiService(type = ServiceType.class, name = "json node filter service", description = "json node filter")
    public static class ServiceWithJsonNodeFilter
    {
        @ApiList(description = "json node filter")
        public List<ResourceWithJsonNodeField> list(@ApiParameter ApiFilter json)
        {
            return null;
        }
    }

    @ApiService(type = ServiceType.class, name = "json node order by service", description = "json node order by")
    public static class ServiceWithJsonNodeOrderBy
    {
        @ApiList(description = "json node order by")
        public List<ResourceWithJsonNodeField> list(@ApiParameter(allowedValues = "json") ApiOrderBy orderBy)
        {
            return null;
        }
    }

    @ApiService(type = ServiceType.class, name = "poly json node filter service", description = "poly json node filter")
    public static class ServiceWithPolyJsonNodeFilter
    {
        @ApiList(description = "poly json node filter")
        public List<PolyResourceWithJsonNodeField> list(@ApiParameter ApiFilter json)
        {
            return null;
        }
    }

    @ApiPolyResource(key = "type", name = "polyJsonNodeField", description = "poly json node field")
    public sealed interface PolyResourceWithJsonNodeField
    {
        @ApiResource(name = "polyJsonNode", description = "poly json node")
        record PolyJsonNode(@ApiDescription("json") ApiJsonNode json)
                implements PolyResourceWithJsonNodeField {}
    }

    @ApiResource(name = "jsonNodeField", description = "json node field")
    public record ResourceWithJsonNodeField(@ApiDescription("json") ApiJsonNode json) {}

    @ApiResource(name = "jacksonJsonNodeField", description = "jackson json node field")
    public record ResourceWithJacksonJsonNodeField(@ApiDescription("json") JsonNode json) {}

    @ApiResource(name = "objectNodeField", description = "object node field")
    public record ResourceWithObjectNodeField(@ApiDescription("object") ObjectNode object) {}

    @ApiResource(name = "arrayNodeField", description = "array node field")
    public record ResourceWithArrayNodeField(@ApiDescription("array") ArrayNode array) {}

    @ApiResource(name = "textNodeField", description = "text node field")
    public record ResourceWithTextNodeField(@ApiDescription("text") TextNode text) {}

    @ApiResource(name = "intNodeField", description = "int node field")
    public record ResourceWithIntNodeField(@ApiDescription("int") IntNode integer) {}

    @ApiResource(name = "nullNodeField", description = "null node field")
    public record ResourceWithNullNodeField(@ApiDescription("null") NullNode nullNode) {}

    @Test
    public void testPolyResourceWithReusedKey()
    {
        ValidationContext validationContext = new ValidationContext();

        ModelResource modelResource = resourceBuilder(PolyResourceWithReusedKey.class).build();
        validateResource(validationContext, METADATA, modelResource, CONFIG.enumValueResolver());

        assertThat(validationContext.errors())
                .anyMatch(s -> s.contains("ItsNotOk is a sub-resource of ApiPolyResource and has a component that is the same as the ApiPolyResource key: dontReuse"));
    }

    @Test
    public void testStreamResponse()
    {
        ModelServices services = ApiBuilder.apiBuilder().add(ServiceWithStreamResponse.class).build().modelServices();
        assertThat(services.errors()).hasSize(0);

        services = ApiBuilder.apiBuilder().add(ServiceWithCreateStreamResponse.class).build().modelServices();
        assertThat(services.errors()).hasSize(0);

        services = ApiBuilder.apiBuilder().add(BadServiceWithStreamResponse1.class).build().modelServices();
        assertThat(services.errors()).hasSize(1).first()
                .matches(s -> s.startsWith("ApiStreamResponse is only allowed for @ApiGet or @ApiCreate"));

        services = ApiBuilder.apiBuilder().add(BadServiceWithStreamResponse2.class).build().modelServices();
        assertThat(services.errors()).anyMatch(s -> s.startsWith("ApiStreamResponse cannot be a parameter"));
    }

    @Test
    public void testMultiPartForm()
    {
        ModelServices services = ApiBuilder.apiBuilder().add(ServiceWithMultiPartForm.class).build().modelServices();
        assertThat(services.errors()).hasSize(0);

        services = ApiBuilder.apiBuilder().add(BadServiceWithMultiPartForm.class).build().modelServices();
        assertThat(services.errors()).hasSize(1).first()
                .matches(s -> s.startsWith("ApiMultiPartForm is not allowed for method returns"));
    }

    @Test
    public void testRecursiveResource()
    {
        ValidationContext validationContext = new ValidationContext();
        ModelResource modelResource = resourceBuilder(RecursiveModel.class).build();

        validateResource(validationContext, METADATA, modelResource, CONFIG.enumValueResolver());

        modelResource.components().forEach(component -> {
            if (component.type().equals(RecursiveModel.class)) {
                assertThat(component.modifiers()).contains(RECURSIVE_REFERENCE);
            }
            else {
                List<ModelResource> subTypeComponents = component.components();
                assertThat(subTypeComponents).hasSize(1);
                assertThat(subTypeComponents.getFirst().modifiers()).contains(RECURSIVE_REFERENCE);
            }
        });

        ValidationContext validationContext2 = new ValidationContext();
        validationContext2.inContext("", _ -> validateResource(validationContext2, METADATA, resourceBuilder(BadRecursiveModel1.class).build(), CONFIG.enumValueResolver()));
        assertThat(validationContext2.errors()).isNotEmpty().allMatch(s -> s.startsWith("Recursive resources are only allowed in collections"));

        ValidationContext validationContext3 = new ValidationContext();
        validationContext3.inContext("", _ -> validateResource(validationContext3, METADATA, resourceBuilder(BadRecursiveModel2.class).build(), CONFIG.enumValueResolver()));
        assertThat(validationContext3.errors()).isNotEmpty().allMatch(s -> s.startsWith("Recursive resources are only allowed in collections"));

        ValidationContext validationContext4 = new ValidationContext();
        validationContext4.inContext("", _ -> validateResource(validationContext4, METADATA, resourceBuilder(BadRecursiveModel3.class).build(), CONFIG.enumValueResolver()));
        assertThat(validationContext4.errors()).isNotEmpty().allMatch(s -> s.startsWith("Recursive resources are only allowed in collections"));

        ValidationContext validationContext5 = new ValidationContext();
        validationContext5.inContext("", _ -> validateResource(validationContext5, METADATA, resourceBuilder(BadRecursiveModel4.class).build(), CONFIG.enumValueResolver()));
        assertThat(validationContext5.errors()).isNotEmpty().allMatch(s -> s.startsWith("Maps in resources must be Map<String, String>"));
    }

    @Test
    public void testRecursiveSerialization()
    {
        PolyResourceModule.Builder builder = PolyResourceModule.builder();
        builder.addPolyResource(RecursiveResourceBase.class);
        Module module = builder.build();
        Injector injector = Guice.createInjector(module, new JsonModule());
        JsonMapper jsonMapper = injector.getInstance(JsonMapper.class);

        ResourceSerializationValidator serializationValidator = new ResourceSerializationValidator(ImmutableSet.of(RecursiveResource.class));
        serializationValidator.validateSerialization(jsonMapper);
    }

    private static void assertIdComponent(ModelResource resource, String name, Class<?> type, ModelResourceType resourceType, boolean optional)
    {
        ModelResource component = resource.components().stream()
                .filter(candidate -> candidate.name().equals(name))
                .findFirst()
                .orElseThrow();

        assertThat(component.type()).isEqualTo(type);
        assertThat(component.resourceType()).isEqualTo(resourceType);
        if (optional) {
            assertThat(component.modifiers()).contains(OPTIONAL);
        }
        else {
            assertThat(component.modifiers()).doesNotContain(OPTIONAL);
        }
    }

    private static List<String> openApiEnumValues(Class<?> serviceClass, String schemaName, String propertyName)
    {
        return openApiProperty(serviceClass, schemaName, propertyName).getEnum().stream()
                .map(String.class::cast)
                .toList();
    }

    private static List<String> openApiEnumValues(Class<?> serviceClass, String schemaName, String propertyName, ApiBuilderConfig config)
    {
        return openApiProperty(serviceClass, schemaName, propertyName, config).getEnum().stream()
                .map(String.class::cast)
                .toList();
    }

    private static Schema<?> openApiProperty(Class<?> serviceClass, String schemaName, String propertyName)
    {
        return openApiProperty(serviceClass, schemaName, propertyName, CONFIG);
    }

    private static Schema<?> openApiProperty(Class<?> serviceClass, String schemaName, String propertyName, ApiBuilderConfig config)
    {
        ModelApi modelApi = ApiBuilder.apiBuilder(config).add(serviceClass).build();
        assertThat(modelApi.modelServices().errors()).isEmpty();

        ModelServiceType serviceType = modelApi.modelServices().services().iterator().next().service().type();
        OpenAPI openAPI = OpenApiProvider.create(modelApi.modelServices(), new OpenApiMetadata(Optional.empty(), ImmutableList.of()), config).build(serviceType, _ -> true);
        Map<String, Schema> schemas = openAPI.getComponents().getSchemas();
        Schema<?> schema = schemas.get(schemaName);
        assertThat(schema).withFailMessage("Schema not found: %s. Found: %s", schemaName, schemas.keySet()).isNotNull();
        Schema<?> property = schema.getProperties().get(propertyName);
        assertThat(property).isNotNull();
        return property;
    }

    private static ResourceBuilder resourceBuilder(Type resource)
    {
        return ResourceBuilder.resourceBuilder(resource, CONFIG.enumValueResolver());
    }

    private static class ConfiguredEnumValueResolver
            implements ApiEnumValueResolver
    {
        @Override
        public List<String> values(Class<?> enumClass)
        {
            ImmutableList.Builder<String> values = ImmutableList.builder();
            for (Object constant : enumClass.getEnumConstants()) {
                values.add(value((Enum<?>) constant));
            }
            return values.build();
        }

        @Override
        public String value(Enum<?> value)
        {
            StringBuilder builder = new StringBuilder("Configured");
            for (String part : value.name().split("_")) {
                builder.append(part.charAt(0))
                        .append(part.substring(1).toLowerCase(Locale.US));
            }
            return builder.toString();
        }
    }

    public static class EnumServiceType
            implements ApiServiceType
    {
        @Override
        public String id()
        {
            return "enum";
        }

        @Override
        public int version()
        {
            return 1;
        }

        @Override
        public String title()
        {
            return "enum";
        }

        @Override
        public String description()
        {
            return "enum";
        }

        @Override
        public Set<ApiServiceTrait> traits()
        {
            return ImmutableSet.of();
        }
    }

    public static class UpperSnakeCaseEnumServiceType
            extends EnumServiceType
    {
        @Override
        public ApiEnumNamingFormat enumNamingFormat()
        {
            return ApiEnumNamingFormat.UPPER_SNAKE_CASE;
        }
    }

    @ApiService(type = EnumServiceType.class, name = "enum", description = "enum")
    public static class PascalCaseEnumService
    {
        @ApiCreate(description = "create")
        public void create(PascalCaseEnumResource resource) {}
    }

    @ApiResource(name = "pascalCaseEnumResource", description = "enum")
    public record PascalCaseEnumResource(PascalCaseEnum value) {}

    public enum PascalCaseEnum
    {
        Small,
        Large,
    }

    @ApiService(type = UpperSnakeCaseEnumServiceType.class, name = "enum", description = "enum")
    public static class UpperSnakeCaseEnumService
    {
        @ApiCreate(description = "create")
        public void create(UpperSnakeCaseEnumResource resource) {}
    }

    @ApiResource(name = "upperSnakeCaseEnumResource", description = "enum")
    public record UpperSnakeCaseEnumResource(UpperSnakeCaseEnum value) {}

    public enum UpperSnakeCaseEnum
    {
        SMALL_VALUE,
        LARGE_VALUE,
    }

    @ApiService(type = EnumServiceType.class, name = "enum", description = "enum")
    public static class DefaultUpperSnakeCaseEnumService
    {
        @ApiCreate(description = "create")
        public void create(UpperSnakeCaseEnumResource resource) {}
    }

    @ApiService(type = EnumServiceType.class, name = "enum", description = "enum")
    public static class InvalidEnumService
    {
        @ApiCreate(description = "create")
        public void create(InvalidEnumResource resource) {}
    }

    @ApiResource(name = "invalidEnumResource", description = "enum")
    public record InvalidEnumResource(InvalidEnum value) {}

    public enum InvalidEnum
    {
        small
    }

    @ApiService(type = EnumServiceType.class, name = "enum", description = "enum")
    public static class JsonValueToStringEnumService
    {
        @ApiCreate(description = "create")
        public void create(JsonValueToStringEnumResource resource) {}
    }

    @ApiResource(name = "jsonValueToStringEnumResource", description = "enum")
    public record JsonValueToStringEnumResource(JsonValueToStringEnum value) {}

    public enum JsonValueToStringEnum
    {
        Small;

        @JsonValue
        @Override
        public String toString()
        {
            return "Small";
        }
    }

    @ApiService(type = EnumServiceType.class, name = "enum", description = "enum")
    public static class CustomJsonValueEnumService
    {
        @ApiCreate(description = "create")
        public void create(CustomJsonValueEnumResource resource) {}
    }

    @ApiResource(name = "customJsonValueEnumResource", description = "enum")
    public record CustomJsonValueEnumResource(CustomJsonValueEnum value) {}

    public enum CustomJsonValueEnum
    {
        @ApiDescription("small value")
        Small("SmallWireValue"),
        @ApiDescription("large value")
        Large("LargeWireValue");

        private final String value;

        CustomJsonValueEnum(String value)
        {
            this.value = value;
        }

        @JsonValue
        public String value()
        {
            return value;
        }
    }

    @ApiService(type = EnumServiceType.class, name = "enum", description = "enum")
    public static class NonStringJsonValueEnumService
    {
        @ApiCreate(description = "create")
        public void create(NonStringJsonValueEnumResource resource) {}
    }

    @ApiResource(name = "nonStringJsonValueEnumResource", description = "enum")
    public record NonStringJsonValueEnumResource(NonStringJsonValueEnum value) {}

    public enum NonStringJsonValueEnum
    {
        Small;

        @JsonValue
        public int value()
        {
            return 1;
        }
    }

    @ApiService(type = EnumServiceType.class, name = "enum", description = "enum")
    public static class NullJsonValueEnumService
    {
        @ApiCreate(description = "create")
        public void create(NullJsonValueEnumResource resource) {}
    }

    @ApiResource(name = "nullJsonValueEnumResource", description = "enum")
    public record NullJsonValueEnumResource(NullJsonValueEnum value) {}

    public enum NullJsonValueEnum
    {
        Small;

        @JsonValue
        public String value()
        {
            return null;
        }
    }

    @ApiService(type = EnumServiceType.class, name = "enum", description = "enum")
    public static class DuplicateJsonValueEnumService
    {
        @ApiCreate(description = "create")
        public void create(DuplicateJsonValueEnumResource resource) {}
    }

    @ApiResource(name = "duplicateJsonValueEnumResource", description = "enum")
    public record DuplicateJsonValueEnumResource(DuplicateJsonValueEnum value) {}

    public enum DuplicateJsonValueEnum
    {
        Small,
        Large;

        @JsonValue
        public String value()
        {
            return "Duplicate";
        }
    }

    @ApiService(type = EnumServiceType.class, name = "enum", description = "enum")
    public static class MultipleJsonValueEnumService
    {
        @ApiCreate(description = "create")
        public void create(MultipleJsonValueEnumResource resource) {}
    }

    @ApiResource(name = "multipleJsonValueEnumResource", description = "enum")
    public record MultipleJsonValueEnumResource(MultipleJsonValueEnum value) {}

    public enum MultipleJsonValueEnum
    {
        Small;

        @JsonValue
        @Override
        public String toString()
        {
            return "Small";
        }

        @JsonValue
        public String value()
        {
            return "Small";
        }
    }

    @ApiService(type = EnumServiceType.class, name = "enum", description = "enum")
    public static class StaticJsonValueEnumService
    {
        @ApiCreate(description = "create")
        public void create(StaticJsonValueEnumResource resource) {}
    }

    @ApiResource(name = "staticJsonValueEnumResource", description = "enum")
    public record StaticJsonValueEnumResource(StaticJsonValueEnum value) {}

    public enum StaticJsonValueEnum
    {
        Small;

        @JsonValue
        public static String value()
        {
            return "Small";
        }
    }
}
