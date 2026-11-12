package io.airlift.api.servertests.typedorderby;

import com.fasterxml.jackson.annotation.JsonValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.reflect.TypeToken;
import io.airlift.api.ApiBuilderConfig;
import io.airlift.api.ApiCustom;
import io.airlift.api.ApiDescription;
import io.airlift.api.ApiEnumNamingFormat;
import io.airlift.api.ApiList;
import io.airlift.api.ApiOrderBy;
import io.airlift.api.ApiOrderByDirection;
import io.airlift.api.ApiPaginatedResult;
import io.airlift.api.ApiPagination;
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
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static io.airlift.api.ApiEnumNamingFormat.UPPER_SNAKE_CASE;
import static io.airlift.api.ApiOrderBy.ORDER_BY_PARAMETER_NAME;
import static io.airlift.api.ApiPaginatedResult.EMPTY_PAGE_TOKEN;
import static io.airlift.api.ApiPagination.PAGE_SIZE_QUERY_PARAMETER_NAME;
import static io.airlift.api.ApiPagination.PAGE_TOKEN_QUERY_PARAMETER_NAME;
import static io.airlift.api.ApiType.LIST;
import static io.airlift.http.client.FullJsonResponseHandler.createFullJsonResponseHandler;
import static io.airlift.http.client.Request.Builder.prepareGet;
import static io.airlift.http.client.StringResponseHandler.createStringResponseHandler;
import static io.airlift.json.JsonCodec.jsonCodec;
import static java.util.stream.Collectors.joining;
import static org.assertj.core.api.Assertions.assertThat;

public class TestTypedApiOrderBy
        extends ServerTestBase
{
    private static final String TYPED_ORDER_BY_PATH = "/typed/api/v1/typedOrderByResult";
    private static final JsonCodec<TypedOrderByResult[]> TYPED_ORDER_BY_RESULT_CODEC = jsonCodec(TypedOrderByResult[].class);
    private static final JsonCodec<ApiPaginatedResult<TypedOrderByResult>> PAGINATED_ORDER_BY_RESULT_CODEC = jsonCodec(new TypeToken<>() {});

    public TestTypedApiOrderBy()
    {
        super(TypedOrderByService.class);
    }

    @Test
    public void testTypedOrderBy()
    {
        URI uri = UriBuilder.fromUri(baseUri)
                .path(TYPED_ORDER_BY_PATH)
                .queryParam(ORDER_BY_PARAMETER_NAME, "DISPLAY_NAME ASC,CREATED_AT DESC")
                .build();

        JsonResponse<TypedOrderByResult[]> response = httpClient.execute(prepareGet().setUri(uri).build(), createFullJsonResponseHandler(TYPED_ORDER_BY_RESULT_CODEC));

        assertThat(response.getStatusCode()).isEqualTo(200);
        assertThat(response.getValue()).containsExactly(new TypedOrderByResult(
                ImmutableList.of("DISPLAY_NAME", "CREATED_AT"),
                ImmutableList.of("ASCENDING", "DESCENDING")));
    }

    @Test
    public void testMissingTypedOrderBy()
    {
        URI uri = UriBuilder.fromUri(baseUri)
                .path(TYPED_ORDER_BY_PATH)
                .build();

        JsonResponse<TypedOrderByResult[]> response = httpClient.execute(prepareGet().setUri(uri).build(), createFullJsonResponseHandler(TYPED_ORDER_BY_RESULT_CODEC));

        assertThat(response.getStatusCode()).isEqualTo(200);
        assertThat(response.getValue()).containsExactly(new TypedOrderByResult(ImmutableList.of(), ImmutableList.of()));
    }

    @ParameterizedTest
    @MethodSource("invalidTypedOrderBy")
    public void testInvalidTypedOrderBy(String orderBy)
    {
        URI uri = UriBuilder.fromUri(baseUri)
                .path(TYPED_ORDER_BY_PATH)
                .queryParam(ORDER_BY_PARAMETER_NAME, orderBy)
                .build();

        StringResponse response = httpClient.execute(prepareGet().setUri(uri).build(), createStringResponseHandler());
        assertThat(response.getStatusCode()).isEqualTo(400);
    }

    private static Stream<Arguments> invalidTypedOrderBy()
    {
        return Stream.of(
                Arguments.of("DisplayName ASC"),
                Arguments.of("DISPLAY_NAME DOWN"),
                Arguments.of("NOT_A_FIELD ASC"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"raw", "object", "string"})
    public void testStringOrderBy(String verb)
    {
        URI uri = UriBuilder.fromUri(baseUri)
                .path(TYPED_ORDER_BY_PATH + ":" + verb)
                .queryParam(ORDER_BY_PARAMETER_NAME, "displayName,createdAt desc")
                .build();

        JsonResponse<TypedOrderByResult[]> response = httpClient.execute(prepareGet().setUri(uri).build(), createFullJsonResponseHandler(TYPED_ORDER_BY_RESULT_CODEC));

        assertThat(response.getStatusCode()).isEqualTo(200);
        assertThat(response.getValue()).containsExactly(new TypedOrderByResult(
                ImmutableList.of("displayName", "createdAt"),
                ImmutableList.of("ASCENDING", "DESCENDING")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"raw", "object", "string"})
    public void testMissingStringOrderBy(String verb)
    {
        URI uri = UriBuilder.fromUri(baseUri)
                .path(TYPED_ORDER_BY_PATH + ":" + verb)
                .build();

        JsonResponse<TypedOrderByResult[]> response = httpClient.execute(prepareGet().setUri(uri).build(), createFullJsonResponseHandler(TYPED_ORDER_BY_RESULT_CODEC));

        assertThat(response.getStatusCode()).isEqualTo(200);
        assertThat(response.getValue()).containsExactly(new TypedOrderByResult(ImmutableList.of(), ImmutableList.of()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"raw", "object", "string"})
    public void testInvalidStringOrderBy(String verb)
    {
        for (String orderBy : List.of("unknown ASC", "displayName DOWN")) {
            URI uri = UriBuilder.fromUri(baseUri)
                    .path(TYPED_ORDER_BY_PATH + ":" + verb)
                    .queryParam(ORDER_BY_PARAMETER_NAME, orderBy)
                    .build();

            StringResponse response = httpClient.execute(prepareGet().setUri(uri).build(), createStringResponseHandler());
            assertThat(response.getStatusCode()).isEqualTo(400);
        }
    }

    @Test
    public void testEnumWireValues()
    {
        ApiOrderBy<WireOrderByField> orderBy = new ApiOrderBy<>(ImmutableList.of(
                new ApiOrderBy.Ordering<>(WireOrderByField.DISPLAY_NAME, ApiOrderByDirection.ASCENDING),
                new ApiOrderBy.Ordering<>(WireOrderByField.CREATED_AT, ApiOrderByDirection.DESCENDING)));
        URI uri = UriBuilder.fromUri(baseUri)
                .path(TYPED_ORDER_BY_PATH + ":wire")
                .queryParam(ORDER_BY_PARAMETER_NAME, orderBy.orderings().stream()
                        .map(ordering -> ordering.toQueryValue(ApiBuilderConfig.jackson().enumValueResolver()))
                        .collect(joining(",")))
                .build();

        JsonResponse<TypedOrderByResult[]> response = httpClient.execute(prepareGet().setUri(uri).build(), createFullJsonResponseHandler(TYPED_ORDER_BY_RESULT_CODEC));

        assertThat(response.getStatusCode()).isEqualTo(200);
        assertThat(response.getValue()).containsExactly(new TypedOrderByResult(
                ImmutableList.of("DISPLAY_NAME", "CREATED_AT"),
                ImmutableList.of("ASCENDING", "DESCENDING")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"DISPLAY_NAME ASC", "display_label ASC", "UNKNOWN ASC"})
    public void testInvalidEnumWireValues(String orderBy)
    {
        URI uri = UriBuilder.fromUri(baseUri)
                .path(TYPED_ORDER_BY_PATH + ":wire")
                .queryParam(ORDER_BY_PARAMETER_NAME, orderBy)
                .build();

        StringResponse response = httpClient.execute(prepareGet().setUri(uri).build(), createStringResponseHandler());
        assertThat(response.getStatusCode()).isEqualTo(400);
    }

    @ParameterizedTest
    @EnumSource(ApiOrderByDirection.class)
    public void testPaginatedEnumOrderBy(ApiOrderByDirection direction)
    {
        ApiPagination<WireOrderByField> pagination = new ApiPagination<>(Optional.empty(), 25, Optional.of(new ApiOrderBy.Ordering<>(WireOrderByField.DISPLAY_NAME, direction)));

        for (int page = 0; page < 2; page++) {
            UriBuilder uriBuilder = UriBuilder.fromUri(baseUri)
                    .path(TYPED_ORDER_BY_PATH + ":paginated")
                    .queryParam(PAGE_SIZE_QUERY_PARAMETER_NAME, pagination.pageSize())
                    .queryParam(ORDER_BY_PARAMETER_NAME, pagination.ordering().orElseThrow().toQueryValue(ApiBuilderConfig.jackson().enumValueResolver()));
            pagination.pageToken().ifPresent(token -> uriBuilder.queryParam(PAGE_TOKEN_QUERY_PARAMETER_NAME, token));

            JsonResponse<ApiPaginatedResult<TypedOrderByResult>> response = httpClient.execute(prepareGet().setUri(uriBuilder.build()).build(), createFullJsonResponseHandler(PAGINATED_ORDER_BY_RESULT_CODEC));

            assertThat(response.getStatusCode()).isEqualTo(200);
            assertThat(response.getValue().result()).containsExactly(new TypedOrderByResult(ImmutableList.of("DISPLAY_NAME"), ImmutableList.of(direction.name())));
            assertThat(response.getValue().nextPageToken()).isEqualTo(page == 0 ? "next" : EMPTY_PAGE_TOKEN);

            pagination = pagination.next(response.getValue());
            assertThat(pagination.pageSize()).isEqualTo(25);
            assertThat(pagination.ordering()).contains(new ApiOrderBy.Ordering<>(WireOrderByField.DISPLAY_NAME, direction));
        }

        assertThat(pagination.pageToken()).isEmpty();
    }

    @Test
    public void testMissingPaginatedEnumOrderBy()
    {
        URI uri = UriBuilder.fromUri(baseUri)
                .path(TYPED_ORDER_BY_PATH + ":paginated")
                .build();

        JsonResponse<ApiPaginatedResult<TypedOrderByResult>> response = httpClient.execute(prepareGet().setUri(uri).build(), createFullJsonResponseHandler(PAGINATED_ORDER_BY_RESULT_CODEC));

        assertThat(response.getStatusCode()).isEqualTo(200);
        assertThat(response.getValue().result()).containsExactly(new TypedOrderByResult(ImmutableList.of(), ImmutableList.of()));
        assertThat(response.getValue().nextPageToken()).isEqualTo("next");
    }

    @ParameterizedTest
    @ValueSource(strings = {"NOT_A_FIELD ASC", "DISPLAY_LABEL DOWN", "DISPLAY_LABEL ASC,CREATION_TIME DESC"})
    public void testInvalidPaginatedEnumOrderBy(String orderBy)
    {
        URI uri = UriBuilder.fromUri(baseUri)
                .path(TYPED_ORDER_BY_PATH + ":paginated")
                .queryParam(ORDER_BY_PARAMETER_NAME, orderBy)
                .build();

        StringResponse response = httpClient.execute(prepareGet().setUri(uri).build(), createStringResponseHandler());
        assertThat(response.getStatusCode()).isEqualTo(400);
    }

    @ApiService(type = TypedOrderByServiceType.class, name = "typedOrderBy", description = "typed order by")
    public static class TypedOrderByService
    {
        @ApiList(description = "typed order by")
        public List<TypedOrderByResult> list(@ApiParameter ApiOrderBy<OrderByField> orderBy)
        {
            return ImmutableList.of(new TypedOrderByResult(
                    orderBy.orderings().stream()
                            .map(ApiOrderBy.Ordering::field)
                            .map(Enum::name)
                            .toList(),
                    orderBy.orderings().stream()
                            .map(ApiOrderBy.Ordering::direction)
                            .map(ApiOrderByDirection::name)
                            .toList()));
        }

        @SuppressWarnings("rawtypes")
        @ApiCustom(type = LIST, verb = "raw", description = "raw order by")
        public List<TypedOrderByResult> raw(@ApiParameter(allowedValues = {"displayName", "createdAt"}) ApiOrderBy orderBy)
        {
            return stringResult(orderBy);
        }

        @ApiCustom(type = LIST, verb = "object", description = "object order by")
        public List<TypedOrderByResult> object(@ApiParameter(allowedValues = {"displayName", "createdAt"}) ApiOrderBy<Object> orderBy)
        {
            return stringResult(orderBy);
        }

        @ApiCustom(type = LIST, verb = "string", description = "string order by")
        public List<TypedOrderByResult> string(@ApiParameter(allowedValues = {"displayName", "createdAt"}) ApiOrderBy<String> orderBy)
        {
            return stringResult(orderBy);
        }

        @ApiCustom(type = LIST, verb = "wire", description = "enum wire value order by")
        public List<TypedOrderByResult> wire(@ApiParameter ApiOrderBy<WireOrderByField> orderBy)
        {
            return stringResult(orderBy);
        }

        @ApiCustom(type = LIST, verb = "paginated", description = "paginated enum order by")
        public ApiPaginatedResult<TypedOrderByResult> paginated(
                @ApiParameter ApiPagination<WireOrderByField> pagination,
                @ApiParameter ApiOrderBy<WireOrderByField> orderBy)
        {
            ApiPagination<WireOrderByField> orderedPagination = pagination.withOrdering(orderBy);
            String nextPageToken = orderedPagination.pageToken().isEmpty() ? "next" : EMPTY_PAGE_TOKEN;

            return new ApiPaginatedResult<>(nextPageToken, ImmutableList.of(new TypedOrderByResult(
                    orderedPagination.ordering().map(ApiOrderBy.Ordering::field).map(Enum::name).stream().toList(),
                    orderedPagination.ordering().map(ApiOrderBy.Ordering::direction).map(ApiOrderByDirection::name).stream().toList())));
        }

        private static List<TypedOrderByResult> stringResult(ApiOrderBy<?> orderBy)
        {
            return ImmutableList.of(new TypedOrderByResult(
                    orderBy.orderings().stream()
                            .map(ApiOrderBy.Ordering::field)
                            .map(Object::toString)
                            .toList(),
                    orderBy.orderings().stream()
                            .map(ApiOrderBy.Ordering::direction)
                            .map(ApiOrderByDirection::name)
                            .toList()));
        }
    }

    @ApiResource(name = "typedOrderByResult", description = "typed order by result")
    public record TypedOrderByResult(
            @ApiDescription("fields") List<String> fields,
            @ApiDescription("directions") List<String> directions) {}

    public enum OrderByField
    {
        DISPLAY_NAME,
        CREATED_AT,
    }

    public enum WireOrderByField
    {
        DISPLAY_NAME("DISPLAY_LABEL"),
        CREATED_AT("CREATION_TIME");

        private final String wireValue;

        WireOrderByField(String wireValue)
        {
            this.wireValue = wireValue;
        }

        @JsonValue
        public String wireValue()
        {
            return wireValue;
        }
    }

    public static class TypedOrderByServiceType
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
