package io.airlift.api.compatibility;

import com.fasterxml.jackson.annotation.JsonValue;
import com.google.inject.Guice;
import com.google.inject.Module;
import io.airlift.api.ApiBuilderConfig;
import io.airlift.api.ApiDescription;
import io.airlift.api.ApiEnumValueResolver;
import io.airlift.api.ApiFilter;
import io.airlift.api.ApiFilterList;
import io.airlift.api.ApiList;
import io.airlift.api.ApiParameter;
import io.airlift.api.ApiResource;
import io.airlift.api.ApiService;
import io.airlift.api.binding.ApiModule;
import io.airlift.api.builders.ApiBuilder;
import io.airlift.api.compatability.ApiCompatibility;
import io.airlift.api.compatability.ApiCompatibilityTester;
import io.airlift.api.model.ModelApi;
import io.airlift.api.model.ModelService;
import io.airlift.api.model.ModelServiceMetadata;
import io.airlift.api.model.ModelServiceType;
import io.airlift.api.validation.TestConforming.EnumServiceType;
import io.airlift.json.JsonModule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static com.google.common.collect.MoreCollectors.onlyElement;
import static io.airlift.api.ApiServiceTrait.ENUMS_AS_STRINGS;
import static io.airlift.api.compatability.ApiCompatibilityUtil.methodSpec;
import static io.airlift.api.compatability.ApiCompatibilityUtil.writeMethodSpecFile;
import static org.assertj.core.api.Assertions.assertThat;

public class TestQueryEnumCompatibility
{
    private static final ApiEnumValueResolver ENUM_VALUE_RESOLVER = ApiBuilderConfig.jackson().enumValueResolver();

    @Test
    public void testEnumWireValuesIncludedForEachParameter()
    {
        assertThat(spec(OriginalService.class)).contains(
                "[filter|Field[Created|Name]]",
                "[filters|Field[][Created|Name]]");
    }

    @ParameterizedTest
    @ValueSource(classes = {RemovedValueService.class, RenamedEnumConstantService.class, ChangedWireValueService.class})
    public void testChangedWireValuesChangeSpec(Class<?> serviceClass)
    {
        assertThat(spec(serviceClass)).isNotEqualTo(spec(OriginalService.class));
    }

    @ParameterizedTest
    @ValueSource(classes = {ReorderedService.class, RenamedConstantsService.class})
    public void testUnchangedWireValuesPreserveSpec(Class<?> serviceClass)
    {
        assertThat(spec(serviceClass)).isEqualTo(spec(OriginalService.class));
    }

    @Test
    public void testEnumsAsStringsIgnoreChoices()
    {
        ModelService original = service(OriginalService.class);
        ModelService removed = service(RemovedValueService.class);
        ModelServiceType type = original.service().type();
        ModelServiceMetadata metadata = new ModelServiceMetadata(
                original.service().name(),
                new ModelServiceType(type.id(), type.version(), type.title(), type.description(), Set.of(ENUMS_AS_STRINGS), type.enumNamingFormat()),
                original.service().description(),
                original.service().documentationLinks());

        String originalSpec = methodSpec(metadata, original.methods().getFirst());
        assertThat(originalSpec).doesNotContain("Created", "Name");
        assertThat(methodSpec(metadata, removed.methods().getFirst())).isEqualTo(originalSpec);
    }

    @Test
    public void testConfiguredResolverUsedForStoredSpecs(@TempDir Path directory)
    {
        ApiEnumValueResolver resolver = prefixedResolver("Configured");
        ModelApi api = ApiBuilder.apiBuilder(resolver).add(OriginalService.class).build();
        assertThat(api.modelServices().errors()).isEmpty();
        ModelService service = api.modelServices().services().stream().collect(onlyElement());
        assertThat(methodSpec(service.service(), service.methods().getFirst(), resolver)).contains(
                "[filter|Field[ConfiguredCreated|ConfiguredName]]",
                "[filters|Field[][ConfiguredCreated|ConfiguredName]]");

        ApiCompatibilityTester tester = ApiCompatibilityTester.newDefaultInstance(directory.toString());
        List<String> errors = new ArrayList<>();
        tester.test(api.modelServices(), true, errors::addAll, resolver);
        assertThat(errors).singleElement().asString().contains("Created new compatibility file");

        errors.clear();
        tester.test(api.modelServices(), false, errors::addAll, resolver);
        assertThat(errors).isEmpty();

        tester.test(api.modelServices(), false, errors::addAll, prefixedResolver("Changed"));
        assertThat(errors).singleElement().asString().contains("API change detected", "ConfiguredCreated", "ChangedCreated");
    }

    @Test
    public void testApiModulePassesConfiguredResolver(@TempDir Path directory)
    {
        ModelService service = service(OriginalService.class);
        writeMethodSpecFile(directory.toFile(), service.service(), service.methods().getFirst());

        AtomicInteger calls = new AtomicInteger();
        ApiEnumValueResolver resolver = new ApiEnumValueResolver()
        {
            @Override
            public List<String> values(Class<?> enumClass)
            {
                calls.incrementAndGet();
                return ENUM_VALUE_RESOLVER.values(enumClass);
            }

            @Override
            public String value(Enum<?> value)
            {
                return ENUM_VALUE_RESOLVER.value(value);
            }
        };
        Module module = ApiModule.builder()
                .withApiBuilderConfig(ApiBuilderConfig.of(resolver))
                .addApi(api -> api.add(OriginalService.class))
                .withCompatibilityTester(ApiCompatibilityTester.newDefaultInstance(directory.toString()))
                .build();
        int callsBeforeBinding = calls.get();

        assertThat(Guice.createInjector(module, new JsonModule()).getInstance(ApiCompatibility.class)).isNotNull();
        assertThat(calls.get() - callsBeforeBinding).isEqualTo(2);
    }

    @Test
    public void testStringSignaturesUnchanged()
    {
        ModelService service = service(StringService.class);
        String spec = methodSpec(service.service(), service.methods().getFirst());
        assertThat(spec).contains(
                "[stringFilter|String]",
                "[stringFilters|String[]]",
                "[rawFilter|String]",
                "[rawFilters|String[]]",
                "[objectFilter|String]",
                "[objectFilters|String[]]");
        assertThat(methodSpec(service.service(), service.methods().getFirst(), prefixedResolver("Changed"))).isEqualTo(spec);
    }

    private static String spec(Class<?> serviceClass)
    {
        ModelService service = service(serviceClass);
        return methodSpec(service.service(), service.methods().getFirst());
    }

    private static ModelService service(Class<?> serviceClass)
    {
        ModelApi api = ApiBuilder.apiBuilder().add(serviceClass).build();
        assertThat(api.modelServices().errors()).isEmpty();
        return api.modelServices().services().stream().collect(onlyElement());
    }

    private static ApiEnumValueResolver prefixedResolver(String prefix)
    {
        return new ApiEnumValueResolver()
        {
            @Override
            public List<String> values(Class<?> enumClass)
            {
                return ENUM_VALUE_RESOLVER.values(enumClass).stream().map(value -> prefix + value).toList();
            }

            @Override
            public String value(Enum<?> value)
            {
                return prefix + ENUM_VALUE_RESOLVER.value(value);
            }
        };
    }

    @ApiService(type = EnumServiceType.class, name = "query compatibility", description = "query compatibility")
    public static class OriginalService
    {
        @ApiList(description = "query")
        public List<QueryResult> list(
                @ApiParameter ApiFilter<Field> filter,
                @ApiParameter ApiFilterList<Field> filters)
        {
            return List.of();
        }

        public enum Field
        {
            Name,
            Created,
        }
    }

    @ApiService(type = EnumServiceType.class, name = "query compatibility", description = "query compatibility")
    public static class RemovedValueService
    {
        @ApiList(description = "query")
        public List<QueryResult> list(
                @ApiParameter ApiFilter<Field> filter,
                @ApiParameter ApiFilterList<Field> filters)
        {
            return List.of();
        }

        public enum Field
        {
            Name
        }
    }

    @ApiService(type = EnumServiceType.class, name = "query compatibility", description = "query compatibility")
    public static class RenamedEnumConstantService
    {
        @ApiList(description = "query")
        public List<QueryResult> list(
                @ApiParameter ApiFilter<Field> filter,
                @ApiParameter ApiFilterList<Field> filters)
        {
            return List.of();
        }

        public enum Field
        {
            Name,
            Updated,
        }
    }

    @ApiService(type = EnumServiceType.class, name = "query compatibility", description = "query compatibility")
    public static class ChangedWireValueService
    {
        @ApiList(description = "query")
        public List<QueryResult> list(
                @ApiParameter ApiFilter<Field> filter,
                @ApiParameter ApiFilterList<Field> filters)
        {
            return List.of();
        }

        public enum Field
        {
            Name,
            Created;

            @JsonValue
            public String wireValue()
            {
                return this == Name ? "Name" : "Updated";
            }
        }
    }

    @ApiService(type = EnumServiceType.class, name = "query compatibility", description = "query compatibility")
    public static class ReorderedService
    {
        @ApiList(description = "query")
        public List<QueryResult> list(
                @ApiParameter ApiFilter<Field> filter,
                @ApiParameter ApiFilterList<Field> filters)
        {
            return List.of();
        }

        public enum Field
        {
            Created,
            Name,
        }
    }

    @ApiService(type = EnumServiceType.class, name = "query compatibility", description = "query compatibility")
    public static class RenamedConstantsService
    {
        @ApiList(description = "query")
        public List<QueryResult> list(
                @ApiParameter ApiFilter<Field> filter,
                @ApiParameter ApiFilterList<Field> filters)
        {
            return List.of();
        }

        public enum Field
        {
            LABEL,
            TIMESTAMP;

            @JsonValue
            public String wireValue()
            {
                return this == LABEL ? "Name" : "Created";
            }
        }
    }

    @ApiService(type = EnumServiceType.class, name = "query compatibility", description = "query compatibility")
    public static class StringService
    {
        @SuppressWarnings("rawtypes")
        @ApiList(description = "query")
        public List<QueryResult> list(
                @ApiParameter ApiFilter<String> stringFilter,
                @ApiParameter ApiFilterList<String> stringFilters,
                @ApiParameter ApiFilter rawFilter,
                @ApiParameter ApiFilterList rawFilters,
                @ApiParameter ApiFilter<Object> objectFilter,
                @ApiParameter ApiFilterList<Object> objectFilters)
        {
            return List.of();
        }
    }

    @ApiResource(name = "queryCompatibilityResult", description = "query compatibility result")
    public record QueryResult(@ApiDescription("name") String name) {}
}
