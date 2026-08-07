package io.airlift.api.servertests.typedfilters;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import io.airlift.api.ApiDescription;
import io.airlift.api.ApiEnumNamingFormat;
import io.airlift.api.ApiFilter;
import io.airlift.api.ApiFilterList;
import io.airlift.api.ApiGet;
import io.airlift.api.ApiParameter;
import io.airlift.api.ApiResource;
import io.airlift.api.ApiService;
import io.airlift.api.ApiServiceTrait;
import io.airlift.api.ApiServiceType;
import io.airlift.api.servertests.ServerTestBase;
import io.airlift.http.client.FullJsonResponseHandler.JsonResponse;
import io.airlift.http.client.StringResponseHandler.StringResponse;
import io.airlift.json.JsonCodec;
import jakarta.ws.rs.core.UriBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static io.airlift.api.ApiEnumNamingFormat.UPPER_SNAKE_CASE;
import static io.airlift.http.client.FullJsonResponseHandler.createFullJsonResponseHandler;
import static io.airlift.http.client.Request.Builder.prepareGet;
import static io.airlift.http.client.StringResponseHandler.createStringResponseHandler;
import static io.airlift.json.JsonCodec.jsonCodec;
import static org.assertj.core.api.Assertions.assertThat;

public class TestTypedApiFilters
        extends ServerTestBase
{
    private static final String TYPED_FILTER_PATH = "/typed/api/v1/typedFilterResult";
    private static final String OBJECT_FILTER_PATH = "/typed/api/v1/objectFilterResult";
    private static final JsonCodec<TypedFilterResult> TYPED_FILTER_RESULT_CODEC = jsonCodec(TypedFilterResult.class);
    private static final JsonCodec<ObjectFilterResult> OBJECT_FILTER_RESULT_CODEC = jsonCodec(ObjectFilterResult.class);
    private static final Instant INSTANT_FILTER = Instant.parse("2026-06-10T12:13:14Z");
    private static final Instant INSTANT_FILTER_LIST_VALUE = Instant.parse("2026-06-11T12:13:14Z");
    private static final UUID UUID_FILTER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID UUID_FILTER_LIST_VALUE = UUID.fromString("00000000-0000-0000-0000-000000000002");

    public TestTypedApiFilters()
    {
        super(TypedFilterService.class);
    }

    @Test
    public void testTypedFilters()
    {
        URI uri = UriBuilder.fromUri(baseUri)
                .path(TYPED_FILTER_PATH)
                .queryParam("integerFilter", "42", "99")
                .queryParam("longFilters", "1234567890123", "7")
                .queryParam("booleanFilter", "TrUe")
                .queryParam("doubleFilter", "12.5")
                .queryParam("stringFilter", "hello")
                .queryParam("instantFilter", INSTANT_FILTER.toString())
                .queryParam("instantFilters", INSTANT_FILTER_LIST_VALUE.toString())
                .queryParam("uuidFilter", UUID_FILTER.toString())
                .queryParam("uuidFilters", UUID_FILTER_LIST_VALUE.toString())
                .queryParam("enumFilters", "SMALL_VALUE", "LARGE_VALUE")
                .build();

        JsonResponse<TypedFilterResult> response = httpClient.execute(prepareGet().setUri(uri).build(), createFullJsonResponseHandler(TYPED_FILTER_RESULT_CODEC));

        assertThat(response.getStatusCode()).isEqualTo(200);
        assertThat(response.getValue()).isEqualTo(new TypedFilterResult(
                Optional.of(42),
                ImmutableList.of(1234567890123L, 7L),
                Optional.of(true),
                Optional.of(12.5),
                Optional.of("hello"),
                Optional.of(INSTANT_FILTER),
                ImmutableList.of(INSTANT_FILTER_LIST_VALUE),
                Optional.of(UUID_FILTER),
                ImmutableList.of(UUID_FILTER_LIST_VALUE),
                ImmutableList.of(FilterValue.SMALL_VALUE, FilterValue.LARGE_VALUE)));
    }

    @Test
    public void testMissingTypedFilters()
    {
        URI uri = UriBuilder.fromUri(baseUri)
                .path(TYPED_FILTER_PATH)
                .build();

        JsonResponse<TypedFilterResult> response = httpClient.execute(prepareGet().setUri(uri).build(), createFullJsonResponseHandler(TYPED_FILTER_RESULT_CODEC));

        assertThat(response.getStatusCode()).isEqualTo(200);
        assertThat(response.getValue()).isEqualTo(new TypedFilterResult(
                Optional.empty(),
                ImmutableList.of(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                ImmutableList.of(),
                Optional.empty(),
                ImmutableList.of(),
                ImmutableList.of()));
    }

    @Test
    public void testObjectFiltersUseLegacyBinding()
    {
        URI uri = UriBuilder.fromUri(baseUri)
                .path(OBJECT_FILTER_PATH)
                .queryParam("objectFilter", "not-a-typed-value", "second-value")
                .queryParam("objectFilters", "not-an-integer", "NaN", "Small")
                .build();

        JsonResponse<ObjectFilterResult> response = httpClient.execute(prepareGet().setUri(uri).build(), createFullJsonResponseHandler(OBJECT_FILTER_RESULT_CODEC));

        assertThat(response.getStatusCode()).isEqualTo(200);
        assertThat(response.getValue()).isEqualTo(new ObjectFilterResult(
                Optional.of("not-a-typed-value"),
                ImmutableList.of("not-an-integer", "NaN", "Small")));
    }

    @Test
    public void testMissingObjectFilters()
    {
        URI uri = UriBuilder.fromUri(baseUri)
                .path(OBJECT_FILTER_PATH)
                .build();

        JsonResponse<ObjectFilterResult> response = httpClient.execute(prepareGet().setUri(uri).build(), createFullJsonResponseHandler(OBJECT_FILTER_RESULT_CODEC));

        assertThat(response.getStatusCode()).isEqualTo(200);
        assertThat(response.getValue()).isEqualTo(new ObjectFilterResult(Optional.empty(), ImmutableList.of()));
    }

    @ParameterizedTest
    @MethodSource("invalidTypedFilters")
    public void testInvalidTypedFilters(String parameterName, String parameterValue)
    {
        URI uri = UriBuilder.fromUri(baseUri)
                .path(TYPED_FILTER_PATH)
                .queryParam(parameterName, parameterValue)
                .build();

        StringResponse response = httpClient.execute(prepareGet().setUri(uri).build(), createStringResponseHandler());
        assertThat(response.getStatusCode()).isEqualTo(400);
    }

    private static Stream<Arguments> invalidTypedFilters()
    {
        return Stream.of(
                Arguments.of("integerFilter", "not-integer"),
                Arguments.of("longFilters", "not-long"),
                Arguments.of("booleanFilter", "yes"),
                Arguments.of("doubleFilter", "not-double"),
                Arguments.of("doubleFilter", "NaN"),
                Arguments.of("instantFilter", "not-instant"),
                Arguments.of("instantFilters", "2026-06-10"),
                Arguments.of("uuidFilter", "not-uuid"),
                Arguments.of("uuidFilters", "00000000-0000-0000-0000-00000000000x"),
                Arguments.of("enumFilters", "Small"));
    }

    @ApiService(type = TypedFilterServiceType.class, name = "typedFilter", description = "typed filters")
    public static class TypedFilterService
    {
        @ApiGet(description = "typed filters")
        public TypedFilterResult get(
                @ApiParameter ApiFilter<Integer> integerFilter,
                @ApiParameter ApiFilterList<Long> longFilters,
                @ApiParameter ApiFilter<Boolean> booleanFilter,
                @ApiParameter ApiFilter<Double> doubleFilter,
                @ApiParameter ApiFilter<String> stringFilter,
                @ApiParameter ApiFilter<Instant> instantFilter,
                @ApiParameter ApiFilterList<Instant> instantFilters,
                @ApiParameter ApiFilter<UUID> uuidFilter,
                @ApiParameter ApiFilterList<UUID> uuidFilters,
                @ApiParameter ApiFilterList<FilterValue> enumFilters)
        {
            return new TypedFilterResult(
                    integerFilter.value(),
                    longFilters.values(),
                    booleanFilter.value(),
                    doubleFilter.value(),
                    stringFilter.value(),
                    instantFilter.value(),
                    instantFilters.values(),
                    uuidFilter.value(),
                    uuidFilters.values(),
                    enumFilters.values());
        }

        @ApiGet(description = "object filters")
        public ObjectFilterResult getObjectFilters(
                @ApiParameter ApiFilter<Object> objectFilter,
                @ApiParameter ApiFilterList<Object> objectFilters)
        {
            return new ObjectFilterResult(objectFilter.mapAsString(), objectFilters.mapAsString());
        }
    }

    @ApiResource(name = "typedFilterResult", description = "typed filter result")
    public record TypedFilterResult(
            @ApiDescription("integer") Optional<Integer> integerFilter,
            @ApiDescription("longs") List<Long> longFilters,
            @ApiDescription("boolean") Optional<Boolean> booleanFilter,
            @ApiDescription("double") Optional<Double> doubleFilter,
            @ApiDescription("string") Optional<String> stringFilter,
            @ApiDescription("instant") Optional<Instant> instantFilter,
            @ApiDescription("instants") List<Instant> instantFilters,
            @ApiDescription("uuid") Optional<UUID> uuidFilter,
            @ApiDescription("uuids") List<UUID> uuidFilters,
            @ApiDescription("enums") List<FilterValue> enumFilters) {}

    @ApiResource(name = "objectFilterResult", description = "object filter result")
    public record ObjectFilterResult(
            @ApiDescription("object") Optional<String> objectFilter,
            @ApiDescription("objects") List<String> objectFilters) {}

    public enum FilterValue
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

        @Override
        public ApiEnumNamingFormat enumNamingFormat()
        {
            return UPPER_SNAKE_CASE;
        }
    }
}
