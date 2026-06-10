package io.airlift.api.openapi;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import io.airlift.api.ApiBuilderConfig;
import io.airlift.api.ApiDescription;
import io.airlift.api.ApiEnumNamingFormat;
import io.airlift.api.ApiGet;
import io.airlift.api.ApiParameter;
import io.airlift.api.ApiResource;
import io.airlift.api.ApiService;
import io.airlift.api.ApiServiceTrait;
import io.airlift.api.ApiServiceType;
import io.airlift.api.TypedApiFilter;
import io.airlift.api.TypedApiFilterList;
import io.airlift.api.builders.ApiBuilder;
import io.airlift.api.model.ModelApi;
import io.airlift.api.model.ModelServiceType;
import io.airlift.api.openapi.models.OpenAPI;
import io.airlift.api.openapi.models.Parameter;
import io.airlift.api.openapi.models.Schema;
import io.airlift.api.validation.ResourceWithAllTypes;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static com.google.common.collect.Maps.uniqueIndex;
import static com.google.common.collect.MoreCollectors.onlyElement;
import static io.airlift.api.ApiEnumNamingFormat.UPPER_SNAKE_CASE;
import static io.airlift.api.ApiServiceTrait.ENUMS_AS_STRINGS;
import static io.airlift.api.openapi.OpenApiMetadata.OpenApiVersion.OPENAPI_3_0_1;
import static org.assertj.core.api.Assertions.assertThat;

public class TestTypedApiFilterOpenApi
{
    private static final ApiBuilderConfig CONFIG = ApiBuilderConfig.jackson();

    @Test
    public void testTypedApiFilterSchemas()
    {
        Map<String, Parameter> parameters = parameters(TypedFilterService.class, "/typed/api/v1/typedFilterSchemaResult");

        assertBasicSchema(parameters.get("booleanFilter").getSchema(), "boolean", null);
        assertBasicSchema(parameters.get("integerFilter").getSchema(), "integer", "int32");
        assertBasicSchema(parameters.get("longFilter").getSchema(), "integer", "int64");
        assertBasicSchema(parameters.get("doubleFilter").getSchema(), "number", "double");
        assertBasicSchema(parameters.get("stringFilter").getSchema(), "string", null);
        assertBasicSchema(parameters.get("instantFilter").getSchema(), "string", "date-time");
        assertBasicSchema(parameters.get("uuidFilter").getSchema(), "string", null);
        assertEnumSchema(parameters.get("enumFilter").getSchema(), "Small", "Big", "Large");

        assertArraySchema(parameters.get("booleanFilters").getSchema(), "boolean", null);
        assertArraySchema(parameters.get("integerFilters").getSchema(), "integer", "int32");
        assertArraySchema(parameters.get("longFilters").getSchema(), "integer", "int64");
        assertArraySchema(parameters.get("doubleFilters").getSchema(), "number", "double");
        assertArraySchema(parameters.get("stringFilters").getSchema(), "string", null);
        assertArraySchema(parameters.get("instantFilters").getSchema(), "string", "date-time");
        assertArraySchema(parameters.get("uuidFilters").getSchema(), "string", null);
        assertArrayEnumSchema(parameters.get("enumFilters").getSchema(), "Small", "Big", "Large");
    }

    @Test
    public void testUpperSnakeEnumTypedApiFilterSchemas()
    {
        Map<String, Parameter> parameters = parameters(UpperSnakeTypedFilterService.class, "/typedSnake/api/v1/typedFilterSchemaResult");

        assertEnumSchema(parameters.get("enumFilter").getSchema(), "SMALL_VALUE", "LARGE_VALUE");
        assertArrayEnumSchema(parameters.get("enumFilters").getSchema(), "SMALL_VALUE", "LARGE_VALUE");
    }

    @Test
    public void testTypedApiFilterEnumSchemasRespectEnumsAsStrings()
    {
        Map<String, Parameter> parameters = parameters(TypedFilterEnumsAsStringsService.class, "/typedStrings/api/v1/typedFilterSchemaResult");

        assertEnumAsStringSchema(parameters.get("enumFilter").getSchema());
        assertThat(parameters.get("enumFilters").getSchema().getType()).isEqualTo("array");
        assertEnumAsStringSchema(parameters.get("enumFilters").getSchema().getItems());
    }

    private static Map<String, Parameter> parameters(Class<?> serviceClass, String path)
    {
        ModelApi modelApi = ApiBuilder.apiBuilder(CONFIG).add(serviceClass).build();
        assertThat(modelApi.modelServices().errors()).withFailMessage(() -> modelApi.modelServices().errors().toString()).isEmpty();

        ModelServiceType serviceType = modelApi.modelServices().services().stream()
                .collect(onlyElement())
                .service()
                .type();
        OpenAPI openAPI = OpenApiProvider.create(modelApi.modelServices(), new OpenApiMetadata(Optional.empty(), ImmutableList.of(), "/", Duration.ofMinutes(5), OPENAPI_3_0_1), CONFIG)
                .build(serviceType, _ -> true);

        return uniqueIndex(openAPI.getPaths().get(path).getGet().getParameters(), Parameter::getName);
    }

    private static void assertBasicSchema(Schema<?> schema, String type, String format)
    {
        assertThat(schema.getType()).isEqualTo(type);
        assertThat(schema.getFormat()).isEqualTo(format);
    }

    private static void assertArraySchema(Schema<?> schema, String itemType, String itemFormat)
    {
        assertThat(schema.getType()).isEqualTo("array");
        assertBasicSchema(schema.getItems(), itemType, itemFormat);
    }

    private static void assertEnumSchema(Schema<?> schema, String... values)
    {
        assertThat(schema.getType()).isEqualTo("string");
        assertThat(schema.getEnum().stream()
                .map(Object::toString)
                .toList()).containsExactly(values);
    }

    private static void assertArrayEnumSchema(Schema<?> schema, String... values)
    {
        assertThat(schema.getType()).isEqualTo("array");
        assertEnumSchema(schema.getItems(), values);
    }

    private static void assertEnumAsStringSchema(Schema<?> schema)
    {
        assertThat(schema.getType()).isEqualTo("string");
        assertThat(schema.getEnum()).isNull();
    }

    @ApiService(type = TypedFilterServiceType.class, name = "typedFilter", description = "typed filters")
    public static class TypedFilterService
    {
        @ApiGet(description = "typed filters")
        public TypedFilterSchemaResult get(
                @ApiParameter TypedApiFilter<Boolean> booleanFilter,
                @ApiParameter TypedApiFilter<Integer> integerFilter,
                @ApiParameter TypedApiFilter<Long> longFilter,
                @ApiParameter TypedApiFilter<Double> doubleFilter,
                @ApiParameter TypedApiFilter<String> stringFilter,
                @ApiParameter TypedApiFilter<Instant> instantFilter,
                @ApiParameter TypedApiFilter<UUID> uuidFilter,
                @ApiParameter TypedApiFilter<ResourceWithAllTypes.Stuff> enumFilter,
                @ApiParameter TypedApiFilterList<Boolean> booleanFilters,
                @ApiParameter TypedApiFilterList<Integer> integerFilters,
                @ApiParameter TypedApiFilterList<Long> longFilters,
                @ApiParameter TypedApiFilterList<Double> doubleFilters,
                @ApiParameter TypedApiFilterList<String> stringFilters,
                @ApiParameter TypedApiFilterList<Instant> instantFilters,
                @ApiParameter TypedApiFilterList<UUID> uuidFilters,
                @ApiParameter TypedApiFilterList<ResourceWithAllTypes.Stuff> enumFilters)
        {
            return null;
        }
    }

    @ApiService(type = UpperSnakeTypedFilterServiceType.class, name = "typedFilter", description = "typed filters")
    public static class UpperSnakeTypedFilterService
    {
        @ApiGet(description = "typed filters")
        public TypedFilterSchemaResult get(
                @ApiParameter TypedApiFilter<UpperSnakeFilterValue> enumFilter,
                @ApiParameter TypedApiFilterList<UpperSnakeFilterValue> enumFilters)
        {
            return null;
        }
    }

    @ApiService(type = TypedFilterEnumsAsStringsServiceType.class, name = "typedFilter", description = "typed filters")
    public static class TypedFilterEnumsAsStringsService
    {
        @ApiGet(description = "typed filters")
        public TypedFilterSchemaResult get(
                @ApiParameter TypedApiFilter<ResourceWithAllTypes.Stuff> enumFilter,
                @ApiParameter TypedApiFilterList<ResourceWithAllTypes.Stuff> enumFilters)
        {
            return null;
        }
    }

    @ApiResource(name = "typedFilterSchemaResult", description = "typed filter schema result")
    public record TypedFilterSchemaResult(@ApiDescription("name") String name) {}

    public enum UpperSnakeFilterValue
    {
        SMALL_VALUE,
        LARGE_VALUE,
    }

    public static class TypedFilterServiceType
            implements ApiServiceType
    {
        @Override
        public String id()
        {
            return "typed";
        }

        @Override
        public int version()
        {
            return 1;
        }

        @Override
        public String title()
        {
            return "typed";
        }

        @Override
        public String description()
        {
            return "typed";
        }

        @Override
        public Set<ApiServiceTrait> traits()
        {
            return ImmutableSet.of();
        }
    }

    public static class UpperSnakeTypedFilterServiceType
            extends TypedFilterServiceType
    {
        @Override
        public String id()
        {
            return "typedSnake";
        }

        @Override
        public ApiEnumNamingFormat enumNamingFormat()
        {
            return UPPER_SNAKE_CASE;
        }
    }

    public static class TypedFilterEnumsAsStringsServiceType
            extends TypedFilterServiceType
    {
        @Override
        public String id()
        {
            return "typedStrings";
        }

        @Override
        public Set<ApiServiceTrait> traits()
        {
            return ImmutableSet.of(ENUMS_AS_STRINGS);
        }
    }
}
