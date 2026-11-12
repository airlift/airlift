package io.airlift.api.servertests.filters;

import io.airlift.api.ApiBuilderConfig;
import io.airlift.api.ApiDescription;
import io.airlift.api.ApiFilter;
import io.airlift.api.ApiFilterList;
import io.airlift.api.ApiGet;
import io.airlift.api.ApiParameter;
import io.airlift.api.ApiResource;
import io.airlift.api.ApiService;
import io.airlift.api.model.ModelServiceType;
import io.airlift.api.model.ModelServices;
import io.airlift.api.openapi.OpenApiMetadata;
import io.airlift.api.openapi.OpenApiProvider;
import io.airlift.api.openapi.models.OpenAPI;
import io.airlift.api.openapi.models.Parameter;
import io.airlift.api.responses.ApiBadRequest;
import io.airlift.api.servertests.ServerTestBase;
import io.airlift.api.validation.TestConforming.EnumServiceType;
import io.airlift.http.client.FullJsonResponseHandler.JsonResponse;
import io.airlift.http.client.StringResponseHandler.StringResponse;
import io.airlift.json.JsonCodec;
import jakarta.ws.rs.core.UriBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static com.google.common.collect.Maps.uniqueIndex;
import static com.google.common.collect.MoreCollectors.onlyElement;
import static io.airlift.api.openapi.OpenApiMetadata.OpenApiVersion.OPENAPI_3_0_1;
import static io.airlift.http.client.FullJsonResponseHandler.createFullJsonResponseHandler;
import static io.airlift.http.client.Request.Builder.prepareGet;
import static io.airlift.http.client.StringResponseHandler.createStringResponseHandler;
import static io.airlift.json.JsonCodec.jsonCodec;
import static org.assertj.core.api.Assertions.assertThat;

public class TestFilterAllowedValues
        extends ServerTestBase
{
    private static final String PATH = "/enum/api/v1/allowedFilterResult";
    private static final JsonCodec<AllowedFilterResult> RESULT_CODEC = jsonCodec(AllowedFilterResult.class);

    public TestFilterAllowedValues()
    {
        super(AllowedFilterService.class);
    }

    @Test
    public void testAllowedValuesAndUnrestrictedFilters()
    {
        URI uri = UriBuilder.fromUri(baseUri)
                .path(PATH)
                .queryParam("status", "Active")
                .queryParam("statuses", "Paused", "Active", "Paused")
                .queryParam("unrestricted", "Inactive")
                .queryParam("unrestrictedList", "Inactive", "")
                .build();

        JsonResponse<AllowedFilterResult> response = httpClient.execute(prepareGet().setUri(uri).build(), createFullJsonResponseHandler(RESULT_CODEC));

        assertThat(response.getStatusCode()).isEqualTo(200);
        assertThat(response.getValue()).isEqualTo(new AllowedFilterResult(
                Optional.of("Active"), List.of("Paused", "Active", "Paused"), Optional.of("Inactive"), List.of("Inactive", "")));
    }

    @Test
    public void testMissingFilters()
    {
        URI uri = UriBuilder.fromUri(baseUri).path(PATH).build();
        JsonResponse<AllowedFilterResult> response = httpClient.execute(prepareGet().setUri(uri).build(), createFullJsonResponseHandler(RESULT_CODEC));

        assertThat(response.getStatusCode()).isEqualTo(200);
        assertThat(response.getValue()).isEqualTo(new AllowedFilterResult(Optional.empty(), List.of(), Optional.empty(), List.of()));
    }

    @ParameterizedTest
    @MethodSource("invalidFilters")
    public void testRejectsValuesOutsideAllowedValues(String name, List<String> values, String invalidValue)
    {
        URI uri = UriBuilder.fromUri(baseUri).path(PATH).queryParam(name, values.toArray()).build();
        StringResponse response = httpClient.execute(prepareGet().setUri(uri).build(), createStringResponseHandler());

        assertThat(response.getStatusCode()).isEqualTo(400);
        assertThat(jsonCodec(ApiBadRequest.class).fromJson(response.getBody()).message())
                .contains("Invalid filter %s: %s".formatted(name, invalidValue));
    }

    private static Stream<Arguments> invalidFilters()
    {
        return Stream.of(
                Arguments.of("status", List.of("Inactive"), "Inactive"),
                Arguments.of("status", List.of("active"), "active"),
                Arguments.of("status", List.of(""), ""),
                Arguments.of("statuses", List.of("Inactive"), "Inactive"),
                Arguments.of("statuses", List.of("active"), "active"),
                Arguments.of("statuses", List.of(""), ""),
                Arguments.of("statuses", List.of("Active", "Inactive"), "Inactive"),
                Arguments.of("statuses", List.of("Inactive", "Active"), "Inactive"));
    }

    @Test
    public void testRepeatedSingleFilterStillRejected()
    {
        URI uri = UriBuilder.fromUri(baseUri).path(PATH).queryParam("status", "Active", "Inactive").build();
        StringResponse response = httpClient.execute(prepareGet().setUri(uri).build(), createStringResponseHandler());

        assertThat(response.getStatusCode()).isEqualTo(400);
        assertThat(jsonCodec(ApiBadRequest.class).fromJson(response.getBody()).message())
                .contains("Invalid filter status: multiple values are not allowed");
    }

    @Test
    public void testAllowedValuesMatchOpenApi()
    {
        ModelServices services = injector.getInstance(ModelServices.class);
        ModelServiceType type = services.services().stream().map(service -> service.service().type()).collect(onlyElement());
        OpenAPI openApi = OpenApiProvider.create(
                        services,
                        new OpenApiMetadata(Optional.empty(), List.of(), "/", Duration.ofMinutes(5), OPENAPI_3_0_1),
                        ApiBuilderConfig.jackson())
                .build(type, _ -> true);
        Map<String, Parameter> parameters = uniqueIndex(openApi.getPaths().get(PATH).getGet().getParameters(), Parameter::getName);

        assertThat(parameters.get("status").getSchema().getType()).isEqualTo("string");
        assertThat(parameters.get("status").getSchema().getEnum()).isEqualTo(List.of("Active"));
        assertThat(parameters.get("statuses").getSchema().getType()).isEqualTo("array");
        assertThat(parameters.get("statuses").getSchema().getItems().getEnum()).isEqualTo(List.of("Active", "Paused"));
        assertThat(parameters.get("unrestricted").getSchema().getEnum()).isNull();
        assertThat(parameters.get("unrestrictedList").getSchema().getItems().getEnum()).isNull();
    }

    @SuppressWarnings("rawtypes")
    @ApiService(type = EnumServiceType.class, name = "allowed filters", description = "allowed filters")
    public static class AllowedFilterService
    {
        @ApiGet(description = "filters")
        public AllowedFilterResult get(
                @ApiParameter(allowedValues = "Active") ApiFilter status,
                @ApiParameter(allowedValues = {"Active", "Paused"}) ApiFilterList statuses,
                @ApiParameter ApiFilter unrestricted,
                @ApiParameter ApiFilterList unrestrictedList)
        {
            return new AllowedFilterResult(status.mapAsString(), statuses.mapAsString(), unrestricted.mapAsString(), unrestrictedList.mapAsString());
        }
    }

    @ApiResource(name = "allowedFilterResult", description = "allowed filter result")
    public record AllowedFilterResult(
            @ApiDescription("status") Optional<String> status,
            @ApiDescription("statuses") List<String> statuses,
            @ApiDescription("unrestricted") Optional<String> unrestricted,
            @ApiDescription("unrestricted list") List<String> unrestrictedList) {}
}
