package io.airlift.api.openapi;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import io.airlift.api.ApiBuilderConfig;
import io.airlift.api.ApiDescription;
import io.airlift.api.ApiEnumNamingFormat;
import io.airlift.api.ApiList;
import io.airlift.api.ApiParameter;
import io.airlift.api.ApiResource;
import io.airlift.api.ApiService;
import io.airlift.api.ApiServiceTrait;
import io.airlift.api.ApiServiceType;
import io.airlift.api.TypedApiOrderBy;
import io.airlift.api.builders.ApiBuilder;
import io.airlift.api.model.ModelApi;
import io.airlift.api.model.ModelServiceType;
import io.airlift.api.openapi.models.OpenAPI;
import io.airlift.api.openapi.models.Parameter;
import io.airlift.api.openapi.models.Schema;
import io.airlift.api.validation.ResourceWithAllTypes;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static com.google.common.collect.Maps.uniqueIndex;
import static com.google.common.collect.MoreCollectors.onlyElement;
import static io.airlift.api.ApiEnumNamingFormat.UPPER_SNAKE_CASE;
import static io.airlift.api.ApiOrderBy.ORDER_BY_PARAMETER_NAME;
import static io.airlift.api.ApiServiceTrait.ENUMS_AS_STRINGS;
import static io.airlift.api.openapi.OpenApiMetadata.OpenApiVersion.OPENAPI_3_0_1;
import static org.assertj.core.api.Assertions.assertThat;

public class TestTypedApiOrderByOpenApi
{
    private static final ApiBuilderConfig CONFIG = ApiBuilderConfig.jackson();

    @Test
    public void testTypedApiOrderBySchema()
    {
        Parameter parameter = parameters(TypedOrderByService.class, "/typedOrder/api/v1/typedOrderBySchemaResult").get(ORDER_BY_PARAMETER_NAME);

        assertEnumSchema(parameter.getSchema(), "Small", "Big", "Large");
    }

    @Test
    public void testUpperSnakeTypedApiOrderBySchema()
    {
        Parameter parameter = parameters(UpperSnakeTypedOrderByService.class, "/typedOrderSnake/api/v1/typedOrderBySchemaResult").get(ORDER_BY_PARAMETER_NAME);

        assertEnumSchema(parameter.getSchema(), "DISPLAY_NAME", "CREATED_AT");
    }

    @Test
    public void testTypedApiOrderBySchemaRespectsEnumsAsStrings()
    {
        Parameter parameter = parameters(TypedOrderByEnumsAsStringsService.class, "/typedOrderStrings/api/v1/typedOrderBySchemaResult").get(ORDER_BY_PARAMETER_NAME);

        assertEnumAsStringSchema(parameter.getSchema());
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

    private static void assertEnumSchema(Schema<?> schema, String... values)
    {
        assertThat(schema.getType()).isEqualTo("string");
        assertThat(schema.getEnum().stream()
                .map(Object::toString)
                .toList()).containsExactly(values);
    }

    private static void assertEnumAsStringSchema(Schema<?> schema)
    {
        assertThat(schema.getType()).isEqualTo("string");
        assertThat(schema.getEnum()).isNull();
    }

    @ApiService(type = TypedOrderByServiceType.class, name = "typedOrderBy", description = "typed order by")
    public static class TypedOrderByService
    {
        @ApiList(description = "typed order by")
        public List<TypedOrderBySchemaResult> list(@ApiParameter TypedApiOrderBy<ResourceWithAllTypes.Stuff> orderBy)
        {
            return null;
        }
    }

    @ApiService(type = UpperSnakeTypedOrderByServiceType.class, name = "typedOrderBy", description = "typed order by")
    public static class UpperSnakeTypedOrderByService
    {
        @ApiList(description = "typed order by")
        public List<TypedOrderBySchemaResult> list(@ApiParameter TypedApiOrderBy<OrderByField> orderBy)
        {
            return null;
        }
    }

    @ApiService(type = TypedOrderByEnumsAsStringsServiceType.class, name = "typedOrderBy", description = "typed order by")
    public static class TypedOrderByEnumsAsStringsService
    {
        @ApiList(description = "typed order by")
        public List<TypedOrderBySchemaResult> list(@ApiParameter TypedApiOrderBy<ResourceWithAllTypes.Stuff> orderBy)
        {
            return null;
        }
    }

    @ApiResource(name = "typedOrderBySchemaResult", description = "typed order by schema result")
    public record TypedOrderBySchemaResult(@ApiDescription("name") String name) {}

    public enum OrderByField
    {
        DISPLAY_NAME,
        CREATED_AT,
    }

    public static class TypedOrderByServiceType
            implements ApiServiceType
    {
        @Override
        public String id()
        {
            return "typedOrder";
        }

        @Override
        public int version()
        {
            return 1;
        }

        @Override
        public String title()
        {
            return "typed order";
        }

        @Override
        public String description()
        {
            return "typed order";
        }

        @Override
        public Set<ApiServiceTrait> traits()
        {
            return ImmutableSet.of();
        }
    }

    public static class UpperSnakeTypedOrderByServiceType
            extends TypedOrderByServiceType
    {
        @Override
        public String id()
        {
            return "typedOrderSnake";
        }

        @Override
        public ApiEnumNamingFormat enumNamingFormat()
        {
            return UPPER_SNAKE_CASE;
        }
    }

    public static class TypedOrderByEnumsAsStringsServiceType
            extends TypedOrderByServiceType
    {
        @Override
        public String id()
        {
            return "typedOrderStrings";
        }

        @Override
        public Set<ApiServiceTrait> traits()
        {
            return ImmutableSet.of(ENUMS_AS_STRINGS);
        }
    }
}
