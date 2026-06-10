package io.airlift.api.binding;

import io.airlift.api.ApiBuilderConfig;
import io.airlift.api.ApiDescription;
import io.airlift.api.ApiEnumValueResolver;
import io.airlift.api.ApiFilter;
import io.airlift.api.ApiFilterList;
import io.airlift.api.ApiList;
import io.airlift.api.ApiParameter;
import io.airlift.api.ApiResource;
import io.airlift.api.ApiService;
import io.airlift.api.servertests.ServerTestBase;
import io.airlift.api.validation.TestConforming.EnumServiceType;
import io.airlift.http.client.FullJsonResponseHandler.JsonResponse;
import io.airlift.http.client.StringResponseHandler.StringResponse;
import io.airlift.json.JsonCodec;
import jakarta.ws.rs.core.UriBuilder;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static io.airlift.http.client.FullJsonResponseHandler.createFullJsonResponseHandler;
import static io.airlift.http.client.Request.Builder.prepareGet;
import static io.airlift.http.client.StringResponseHandler.createStringResponseHandler;
import static io.airlift.json.JsonCodec.jsonCodec;
import static org.assertj.core.api.Assertions.assertThat;

public class TestEnumQueryParameterLookup
        extends ServerTestBase
{
    private static final String PATH = "/enum/api/v1/enumLookupResult";
    private static final JsonCodec<EnumLookupResult[]> RESULT_CODEC = jsonCodec(EnumLookupResult[].class);

    private final CountingEnumValueResolver enumValueResolver;

    public TestEnumQueryParameterLookup()
    {
        CountingEnumValueResolver enumValueResolver = new CountingEnumValueResolver();
        super(EnumLookupService.class, builder -> builder.withApiBuilderConfig(ApiBuilderConfig.of(enumValueResolver)));
        this.enumValueResolver = enumValueResolver;
    }

    @Test
    public void testRequestsDoNotResolveEnumValues()
    {
        int resolutionsAtStartup = enumValueResolver.calls.get();
        assertThat(resolutionsAtStartup).isPositive();

        URI uri = UriBuilder.fromUri(baseUri)
                .path(PATH)
                .queryParam("filter", "ConfiguredName")
                .queryParam("filters", "ConfiguredCreated", "ConfiguredName")
                .build();

        for (int request = 0; request < 2; request++) {
            JsonResponse<EnumLookupResult[]> response = httpClient.execute(prepareGet().setUri(uri).build(), createFullJsonResponseHandler(RESULT_CODEC));

            assertThat(response.getStatusCode()).isEqualTo(200);
            assertThat(response.getValue()).containsExactly(new EnumLookupResult(
                    Optional.of("Name"),
                    List.of("Created", "Name")));
        }

        URI missingValues = UriBuilder.fromUri(baseUri).path(PATH).build();
        JsonResponse<EnumLookupResult[]> response = httpClient.execute(prepareGet().setUri(missingValues).build(), createFullJsonResponseHandler(RESULT_CODEC));
        assertThat(response.getStatusCode()).isEqualTo(200);
        assertThat(response.getValue()).containsExactly(new EnumLookupResult(Optional.empty(), List.of()));

        for (String parameter : List.of("filter", "filters")) {
            URI invalidValue = UriBuilder.fromUri(baseUri)
                    .path(PATH)
                    .queryParam(parameter, "Name")
                    .build();
            StringResponse invalidResponse = httpClient.execute(prepareGet().setUri(invalidValue).build(), createStringResponseHandler());
            assertThat(invalidResponse.getStatusCode()).isEqualTo(400);
        }

        assertThat(enumValueResolver.calls.get()).isEqualTo(resolutionsAtStartup);
    }

    private static class CountingEnumValueResolver
            implements ApiEnumValueResolver
    {
        private final ApiEnumValueResolver delegate = ApiBuilderConfig.jackson().enumValueResolver();
        private final AtomicInteger calls = new AtomicInteger();

        @Override
        public List<String> values(Class<?> enumClass)
        {
            return delegate.values(enumClass).stream()
                    .map(value -> "Configured" + value)
                    .toList();
        }

        @Override
        public String value(Enum<?> value)
        {
            calls.incrementAndGet();
            return "Configured" + delegate.value(value);
        }
    }

    @ApiService(type = EnumServiceType.class, name = "enumLookup", description = "enum lookup")
    public static class EnumLookupService
    {
        @ApiList(description = "enum lookup")
        public List<EnumLookupResult> list(
                @ApiParameter ApiFilter<Field> filter,
                @ApiParameter ApiFilterList<Field> filters)
        {
            return List.of(new EnumLookupResult(
                    filter.value().map(Enum::name),
                    filters.values().stream().map(Enum::name).toList()));
        }
    }

    @ApiResource(name = "enumLookupResult", description = "enum lookup result")
    public record EnumLookupResult(
            @ApiDescription("filter") Optional<String> filter,
            @ApiDescription("filters") List<String> filters) {}

    public enum Field
    {
        Name,
        Created,
    }
}
