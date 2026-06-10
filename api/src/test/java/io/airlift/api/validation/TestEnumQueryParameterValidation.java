package io.airlift.api.validation;

import com.google.inject.CreationException;
import com.google.inject.Guice;
import io.airlift.api.ApiBuilderConfig;
import io.airlift.api.ApiCustom;
import io.airlift.api.ApiDescription;
import io.airlift.api.ApiEnumNamingFormat;
import io.airlift.api.ApiEnumValueResolver;
import io.airlift.api.ApiFilter;
import io.airlift.api.ApiFilterList;
import io.airlift.api.ApiList;
import io.airlift.api.ApiParameter;
import io.airlift.api.ApiResource;
import io.airlift.api.ApiService;
import io.airlift.api.ApiServiceTrait;
import io.airlift.api.binding.ApiModule;
import io.airlift.api.builders.ApiBuilder;
import io.airlift.api.validation.TestConforming.DuplicateJsonValueEnum;
import io.airlift.api.validation.TestConforming.EnumServiceType;
import io.airlift.api.validation.TestConforming.MultipleJsonValueEnum;
import io.airlift.api.validation.TestConforming.NonStringJsonValueEnum;
import io.airlift.api.validation.TestConforming.NullJsonValueEnum;
import io.airlift.api.validation.TestConforming.PascalCaseEnum;
import io.airlift.api.validation.TestConforming.StaticJsonValueEnum;
import io.airlift.api.validation.TestConforming.UpperSnakeCaseEnum;
import io.airlift.json.JsonModule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static io.airlift.api.ApiEnumNamingFormat.UPPER_SNAKE_CASE;
import static io.airlift.api.ApiServiceTrait.ENUMS_AS_STRINGS;
import static io.airlift.api.ApiType.LIST;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class TestEnumQueryParameterValidation
{
    private static final List<String> ENUM_PARAMETERS = List.of("filter", "filters");

    @ParameterizedTest
    @ValueSource(classes = {PascalQueryService.class, UpperQueryService.class})
    public void testValidDeclarations(Class<?> serviceClass)
    {
        assertThat(ApiBuilder.apiBuilder().add(serviceClass).build().modelServices().errors()).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("enumFiltersWithAllowedValues")
    public void testEnumFiltersRejectAllowedValues(Class<?> serviceClass, String filterType)
    {
        Set<String> errors = ApiBuilder.apiBuilder().add(serviceClass).build().modelServices().errors();

        assertThat(errors).singleElement().asString()
                .contains(filterType + " parameter derives allowed values from its enum type");
    }

    @ParameterizedTest
    @MethodSource("enumFiltersWithAllowedValues")
    public void testEnumFiltersWithAllowedValuesFailStartupWithoutOpenApi(Class<?> serviceClass, String filterType)
    {
        assertThatThrownBy(() -> Guice.createInjector(
                ApiModule.builder().addApi(api -> api.add(serviceClass)).build(),
                new JsonModule()))
                .isInstanceOf(CreationException.class)
                .hasMessageContaining(filterType + " parameter derives allowed values from its enum type");
    }

    private static Stream<Arguments> enumFiltersWithAllowedValues()
    {
        return Stream.of(
                Arguments.of(EnumFilterWithAllowedValuesService.class, "ApiFilter"),
                Arguments.of(EnumFilterListWithAllowedValuesService.class, "ApiFilterList"),
                Arguments.of(EnumFilterWithAllowedValuesAsStringsService.class, "ApiFilter"),
                Arguments.of(EnumFilterListWithAllowedValuesAsStringsService.class, "ApiFilterList"));
    }

    @ParameterizedTest
    @ValueSource(classes = {InvalidNamingService.class, EnumsAsStringsService.class})
    public void testInvalidNaming(Class<?> serviceClass)
    {
        Set<String> errors = ApiBuilder.apiBuilder().add(serviceClass).build().modelServices().errors();

        assertThat(errors).hasSize(1);
        assertEnumErrors(errors, "list", UpperSnakeCaseEnum.class, "\"SMALL_VALUE\" is not a valid enum name for format PascalCase", ENUM_PARAMETERS);
    }

    @Test
    public void testSameEnumCheckedForEachService()
    {
        Set<String> errors = ApiBuilder.apiBuilder()
                .add(UpperQueryService.class)
                .add(InvalidNamingService.class)
                .build().modelServices().errors();

        assertThat(errors).hasSize(1);
        assertEnumErrors(errors, "list", UpperSnakeCaseEnum.class, "not a valid enum name for format PascalCase", ENUM_PARAMETERS);
    }

    @ParameterizedTest
    @ValueSource(classes = {InvalidNamingService.class, EnumsAsStringsService.class})
    public void testStartupFailsWithoutOpenApi(Class<?> serviceClass)
    {
        assertThatThrownBy(() -> Guice.createInjector(
                ApiModule.builder().addApi(api -> api.add(serviceClass)).build(),
                new JsonModule()))
                .isInstanceOf(CreationException.class)
                .hasMessageContaining("SMALL_VALUE")
                .hasMessageContaining("PascalCase");
    }

    @ParameterizedTest
    @MethodSource("invalidWireValues")
    public void testInvalidWireDefinitions(String methodName, Class<?> enumClass, String reason)
    {
        Set<String> errors = ApiBuilder.apiBuilder().add(InvalidWireService.class).build().modelServices().errors();

        assertThat(errors).hasSize(5);
        assertEnumErrors(errors, methodName, enumClass, reason, ENUM_PARAMETERS);
    }

    private static Stream<Arguments> invalidWireValues()
    {
        return Stream.of(
                Arguments.of("nonString", NonStringJsonValueEnum.class, "must return a String"),
                Arguments.of("nullValue", NullJsonValueEnum.class, "returned null"),
                Arguments.of("duplicates", DuplicateJsonValueEnum.class, "Duplicate enum wire value"),
                Arguments.of("multiple", MultipleJsonValueEnum.class, "Multiple @JsonValue methods"),
                Arguments.of("staticValue", StaticJsonValueEnum.class, "is not an instance method"));
    }

    @Test
    public void testConfiguredResolver()
    {
        ApiBuilderConfig config = ApiBuilderConfig.of(new ApiEnumValueResolver()
        {
            @Override
            public List<String> values(Class<?> enumClass)
            {
                return Arrays.stream(enumClass.getEnumConstants())
                        .map(value -> value((Enum<?>) value))
                        .toList();
            }

            @Override
            public String value(Enum<?> value)
            {
                return "Configured" + value.ordinal();
            }
        });

        assertThat(ApiBuilder.apiBuilder(config).add(InvalidNamingService.class).build().modelServices().errors()).isEmpty();
    }

    private static void assertEnumErrors(Set<String> errors, String methodName, Class<?> enumClass, String reason, List<String> parameters)
    {
        for (String parameter : parameters) {
            assertThat(errors).anySatisfy(error -> assertThat(error).contains(
                    "Method ", methodName, "parameter " + parameter + ",", "enumeration " + enumClass.getTypeName(), reason));
        }
    }

    @ApiService(type = EnumServiceType.class, name = "pascal query", description = "pascal query")
    public static class PascalQueryService
    {
        @ApiList(description = "query")
        public List<QueryResult> list(
                @ApiParameter ApiFilter<PascalCaseEnum> filter,
                @ApiParameter ApiFilterList<PascalCaseEnum> filters)
        {
            return null;
        }
    }

    @ApiService(type = UpperServiceType.class, name = "upper query", description = "upper query")
    public static class UpperQueryService
    {
        @ApiList(description = "query")
        public List<QueryResult> list(
                @ApiParameter ApiFilter<UpperSnakeCaseEnum> filter,
                @ApiParameter ApiFilterList<UpperSnakeCaseEnum> filters)
        {
            return null;
        }
    }

    @ApiService(type = EnumServiceType.class, name = "enum filter with allowed values", description = "enum filter with allowed values")
    public static class EnumFilterWithAllowedValuesService
    {
        @ApiList(description = "query")
        public List<QueryResult> list(@ApiParameter(allowedValues = "Small") ApiFilter<PascalCaseEnum> filter)
        {
            return null;
        }
    }

    @ApiService(type = EnumServiceType.class, name = "enum filter list with allowed values", description = "enum filter list with allowed values")
    public static class EnumFilterListWithAllowedValuesService
    {
        @ApiList(description = "query")
        public List<QueryResult> list(@ApiParameter(allowedValues = "Small") ApiFilterList<PascalCaseEnum> filters)
        {
            return null;
        }
    }

    @ApiService(type = EnumsAsStringsServiceType.class, name = "enum filter with allowed values as strings", description = "enum filter with allowed values as strings")
    public static class EnumFilterWithAllowedValuesAsStringsService
    {
        @ApiList(description = "query")
        public List<QueryResult> list(@ApiParameter(allowedValues = {"Small", "Large"}) ApiFilter<PascalCaseEnum> filter)
        {
            return null;
        }
    }

    @ApiService(type = EnumsAsStringsServiceType.class, name = "enum filter list with allowed values as strings", description = "enum filter list with allowed values as strings")
    public static class EnumFilterListWithAllowedValuesAsStringsService
    {
        @ApiList(description = "query")
        public List<QueryResult> list(@ApiParameter(allowedValues = {"Small", "Large"}) ApiFilterList<PascalCaseEnum> filters)
        {
            return null;
        }
    }

    @ApiService(type = EnumServiceType.class, name = "invalid query naming", description = "invalid query naming")
    public static class InvalidNamingService
    {
        @ApiList(description = "query")
        public List<QueryResult> list(
                @ApiParameter ApiFilter<UpperSnakeCaseEnum> filter,
                @ApiParameter ApiFilterList<UpperSnakeCaseEnum> filters)
        {
            return null;
        }
    }

    @ApiService(type = EnumsAsStringsServiceType.class, name = "query enums as strings", description = "query enums as strings")
    public static class EnumsAsStringsService
    {
        @ApiList(description = "query")
        public List<QueryResult> list(
                @ApiParameter ApiFilter<UpperSnakeCaseEnum> filter,
                @ApiParameter ApiFilterList<UpperSnakeCaseEnum> filters)
        {
            return null;
        }
    }

    @ApiService(type = EnumServiceType.class, name = "invalid query wire values", description = "invalid query wire values")
    public static class InvalidWireService
    {
        @ApiCustom(type = LIST, verb = "nonString", description = "query")
        public List<QueryResult> nonString(
                @ApiParameter ApiFilter<NonStringJsonValueEnum> filter,
                @ApiParameter ApiFilterList<NonStringJsonValueEnum> filters)
        {
            return null;
        }

        @ApiCustom(type = LIST, verb = "nullValue", description = "query")
        public List<QueryResult> nullValue(
                @ApiParameter ApiFilter<NullJsonValueEnum> filter,
                @ApiParameter ApiFilterList<NullJsonValueEnum> filters)
        {
            return null;
        }

        @ApiCustom(type = LIST, verb = "duplicates", description = "query")
        public List<QueryResult> duplicates(
                @ApiParameter ApiFilter<DuplicateJsonValueEnum> filter,
                @ApiParameter ApiFilterList<DuplicateJsonValueEnum> filters)
        {
            return null;
        }

        @ApiCustom(type = LIST, verb = "multiple", description = "query")
        public List<QueryResult> multiple(
                @ApiParameter ApiFilter<MultipleJsonValueEnum> filter,
                @ApiParameter ApiFilterList<MultipleJsonValueEnum> filters)
        {
            return null;
        }

        @ApiCustom(type = LIST, verb = "staticValue", description = "query")
        public List<QueryResult> staticValue(
                @ApiParameter ApiFilter<StaticJsonValueEnum> filter,
                @ApiParameter ApiFilterList<StaticJsonValueEnum> filters)
        {
            return null;
        }
    }

    @ApiResource(name = "enumQueryResult", description = "enum query result")
    public record QueryResult(@ApiDescription("name") String name) {}

    public static class UpperServiceType
            extends EnumServiceType
    {
        @Override
        public String id()
        {
            return "enumSnake";
        }

        @Override
        public ApiEnumNamingFormat enumNamingFormat()
        {
            return UPPER_SNAKE_CASE;
        }
    }

    public static class EnumsAsStringsServiceType
            extends EnumServiceType
    {
        @Override
        public Set<ApiServiceTrait> traits()
        {
            return Set.of(ENUMS_AS_STRINGS);
        }
    }
}
