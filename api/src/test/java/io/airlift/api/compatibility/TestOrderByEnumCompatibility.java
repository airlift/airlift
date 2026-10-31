package io.airlift.api.compatibility;

import io.airlift.api.ApiList;
import io.airlift.api.ApiOrderBy;
import io.airlift.api.ApiPagination;
import io.airlift.api.ApiParameter;
import io.airlift.api.ApiService;
import io.airlift.api.builders.ApiBuilder;
import io.airlift.api.compatibility.TestQueryEnumCompatibility.ChangedWireValueService;
import io.airlift.api.compatibility.TestQueryEnumCompatibility.OriginalService;
import io.airlift.api.compatibility.TestQueryEnumCompatibility.QueryResult;
import io.airlift.api.compatibility.TestQueryEnumCompatibility.RemovedValueService;
import io.airlift.api.compatibility.TestQueryEnumCompatibility.RenamedConstantsService;
import io.airlift.api.compatibility.TestQueryEnumCompatibility.RenamedEnumConstantService;
import io.airlift.api.compatibility.TestQueryEnumCompatibility.ReorderedService;
import io.airlift.api.model.ModelApi;
import io.airlift.api.model.ModelService;
import io.airlift.api.model.ModelServiceMetadata;
import io.airlift.api.model.ModelServiceType;
import io.airlift.api.validation.TestConforming.EnumServiceType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Set;

import static com.google.common.collect.MoreCollectors.onlyElement;
import static io.airlift.api.ApiServiceTrait.ENUMS_AS_STRINGS;
import static io.airlift.api.compatability.ApiCompatibilityUtil.methodSpec;
import static org.assertj.core.api.Assertions.assertThat;

public class TestOrderByEnumCompatibility
{
    @Test
    public void testEnumWireValuesIncluded()
    {
        assertThat(spec(OriginalOrderingService.class)).contains("[orderBy|Field[Created|Name]]");
    }

    @ParameterizedTest
    @ValueSource(classes = {RemovedValueOrderingService.class, RenamedEnumConstantOrderingService.class, ChangedWireValueOrderingService.class})
    public void testChangedWireValuesChangeSpec(Class<?> serviceClass)
    {
        assertThat(spec(serviceClass)).isNotEqualTo(spec(OriginalOrderingService.class));
    }

    @ParameterizedTest
    @ValueSource(classes = {ReorderedOrderingService.class, RenamedConstantsOrderingService.class})
    public void testUnchangedWireValuesPreserveSpec(Class<?> serviceClass)
    {
        assertThat(spec(serviceClass)).isEqualTo(spec(OriginalOrderingService.class));
    }

    @Test
    public void testEnumsAsStringsIgnoreChoices()
    {
        ModelService original = service(OriginalOrderingService.class);
        ModelService removed = service(RemovedValueOrderingService.class);
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
    public void testStringSignaturesUnchanged()
    {
        assertThat(spec(StringOrderingService.class)).contains(
                "[name|created]|[orderBy|String]",
                "[pageToken|String|pageSize|int]");
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

    @ApiService(type = EnumServiceType.class, name = "query compatibility", description = "query compatibility")
    public static class OriginalOrderingService
    {
        @ApiList(description = "query")
        public List<QueryResult> list(@ApiParameter ApiOrderBy<OriginalService.Field> orderBy)
        {
            return List.of();
        }
    }

    @ApiService(type = EnumServiceType.class, name = "query compatibility", description = "query compatibility")
    public static class RemovedValueOrderingService
    {
        @ApiList(description = "query")
        public List<QueryResult> list(@ApiParameter ApiOrderBy<RemovedValueService.Field> orderBy)
        {
            return List.of();
        }
    }

    @ApiService(type = EnumServiceType.class, name = "query compatibility", description = "query compatibility")
    public static class RenamedEnumConstantOrderingService
    {
        @ApiList(description = "query")
        public List<QueryResult> list(@ApiParameter ApiOrderBy<RenamedEnumConstantService.Field> orderBy)
        {
            return List.of();
        }
    }

    @ApiService(type = EnumServiceType.class, name = "query compatibility", description = "query compatibility")
    public static class ChangedWireValueOrderingService
    {
        @ApiList(description = "query")
        public List<QueryResult> list(@ApiParameter ApiOrderBy<ChangedWireValueService.Field> orderBy)
        {
            return List.of();
        }
    }

    @ApiService(type = EnumServiceType.class, name = "query compatibility", description = "query compatibility")
    public static class ReorderedOrderingService
    {
        @ApiList(description = "query")
        public List<QueryResult> list(@ApiParameter ApiOrderBy<ReorderedService.Field> orderBy)
        {
            return List.of();
        }
    }

    @ApiService(type = EnumServiceType.class, name = "query compatibility", description = "query compatibility")
    public static class RenamedConstantsOrderingService
    {
        @ApiList(description = "query")
        public List<QueryResult> list(@ApiParameter ApiOrderBy<RenamedConstantsService.Field> orderBy)
        {
            return List.of();
        }
    }

    @ApiService(type = EnumServiceType.class, name = "query compatibility", description = "query compatibility")
    public static class StringOrderingService
    {
        @ApiList(description = "query")
        public List<QueryResult> list(
                @ApiParameter(allowedValues = {"name", "created"}) ApiOrderBy<String> orderBy,
                @ApiParameter ApiPagination<String> pagination)
        {
            return List.of();
        }
    }
}
