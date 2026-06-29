package io.airlift.api.servertests.typedorderby;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import io.airlift.api.ApiDescription;
import io.airlift.api.ApiEnumNamingFormat;
import io.airlift.api.ApiList;
import io.airlift.api.ApiOrderByDirection;
import io.airlift.api.ApiParameter;
import io.airlift.api.ApiResource;
import io.airlift.api.ApiService;
import io.airlift.api.ApiServiceTrait;
import io.airlift.api.ApiServiceType;
import io.airlift.api.TypedApiOrderBy;
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
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static io.airlift.api.ApiEnumNamingFormat.UPPER_SNAKE_CASE;
import static io.airlift.api.ApiOrderBy.ORDER_BY_PARAMETER_NAME;
import static io.airlift.http.client.FullJsonResponseHandler.createFullJsonResponseHandler;
import static io.airlift.http.client.Request.Builder.prepareGet;
import static io.airlift.http.client.StringResponseHandler.createStringResponseHandler;
import static io.airlift.json.JsonCodec.jsonCodec;
import static org.assertj.core.api.Assertions.assertThat;

public class TestTypedApiOrderBy
        extends ServerTestBase
{
    private static final String TYPED_ORDER_BY_PATH = "/typed/api/v1/typedOrderByResult";
    private static final JsonCodec<TypedOrderByResult[]> TYPED_ORDER_BY_RESULT_CODEC = jsonCodec(TypedOrderByResult[].class);

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

    @ApiService(type = TypedOrderByServiceType.class, name = "typedOrderBy", description = "typed order by")
    public static class TypedOrderByService
    {
        @ApiList(description = "typed order by")
        public List<TypedOrderByResult> list(@ApiParameter TypedApiOrderBy<OrderByField> orderBy)
        {
            return ImmutableList.of(new TypedOrderByResult(
                    orderBy.orderings().stream()
                            .map(TypedApiOrderBy.Ordering::field)
                            .map(Enum::name)
                            .toList(),
                    orderBy.orderings().stream()
                            .map(TypedApiOrderBy.Ordering::direction)
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
