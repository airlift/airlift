package io.airlift.api.servertests.integration;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Closer;
import com.google.common.reflect.TypeToken;
import io.airlift.api.ApiFilter;
import io.airlift.api.ApiFilterList;
import io.airlift.api.ApiOrderBy.Ordering;
import io.airlift.api.ApiPaginatedResult;
import io.airlift.api.ApiPagination;
import io.airlift.api.ApiResourceVersion;
import io.airlift.api.servertests.integration.testingserver.TestingServer;
import io.airlift.api.servertests.integration.testingserver.external.Detail;
import io.airlift.api.servertests.integration.testingserver.external.Detail.NameAndAge;
import io.airlift.api.servertests.integration.testingserver.external.Detail.Schedule;
import io.airlift.api.servertests.integration.testingserver.external.ExternalWidget;
import io.airlift.api.servertests.integration.testingserver.external.ExternalWidgetId;
import io.airlift.api.servertests.integration.testingserver.external.ExternalWidgetSize;
import io.airlift.api.servertests.integration.testingserver.external.NewExternalWidget;
import io.airlift.api.servertests.integration.testingserver.external.RecursiveDetail;
import io.airlift.api.servertests.integration.testingserver.external.StringResult;
import io.airlift.api.servertests.integration.testingserver.internal.InternalController;
import io.airlift.api.servertests.integration.testingserver.internal.InternalWidget;
import io.airlift.http.client.FullJsonResponseHandler;
import io.airlift.http.client.FullJsonResponseHandler.JsonResponse;
import io.airlift.http.client.HttpClientConfig;
import io.airlift.http.client.Request;
import io.airlift.http.client.StreamingResponse;
import io.airlift.http.client.jetty.JettyHttpClient;
import io.airlift.json.JsonCodec;
import io.airlift.json.JsonCodecFactory;
import jakarta.ws.rs.core.UriBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static io.airlift.api.ApiOrderBy.ORDER_BY_PARAMETER_NAME;
import static io.airlift.api.ApiOrderByDirection.ASCENDING;
import static io.airlift.api.ApiOrderByDirection.DESCENDING;
import static io.airlift.api.ApiPagination.DEFAULT_PAGE_SIZE;
import static io.airlift.api.ApiPagination.PAGE_SIZE_QUERY_PARAMETER_NAME;
import static io.airlift.api.ApiPagination.PAGE_TOKEN_QUERY_PARAMETER_NAME;
import static io.airlift.http.client.JsonBodyGenerator.jsonBodyGenerator;
import static io.airlift.http.client.Request.Builder.prepareDelete;
import static io.airlift.http.client.Request.Builder.prepareGet;
import static io.airlift.http.client.Request.Builder.preparePatch;
import static io.airlift.http.client.Request.Builder.preparePost;
import static io.airlift.json.JsonCodec.jsonCodec;
import static io.airlift.json.JsonCodec.mapJsonCodec;
import static java.util.concurrent.ThreadLocalRandom.current;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD;

@TestInstance(PER_CLASS)
@Execution(SAME_THREAD)
public class IntegrationTests
{
    private static final int RECORD_QTY = 1000;
    private static final String BASE_PATH = "public/api/v1";
    private static final String BASE_WIDGET_PATH = BASE_PATH + "/widget";

    private static final JsonCodec<NewExternalWidget> NEW_WIDGET_CODEC = jsonCodec(NewExternalWidget.class);
    private static final JsonCodec<ExternalWidget> EXTERNAL_WIDGET_CODEC = jsonCodec(ExternalWidget.class);
    private static final JsonCodec<ApiPaginatedResult<ExternalWidget>> EXTERNAL_WIDGET_LIST_CODEC = jsonCodec(new TypeToken<>() {});
    private static final JsonCodec<Map<String, String>> MAP_CODEC = mapJsonCodec(String.class, String.class);
    private static final JsonCodec<StringResult> STRING_RESULT_CODEC = jsonCodec(StringResult.class);
    private static final JsonCodec<RecursiveDetail> RECURSIVE_DETAIL_CODEC = jsonCodec(RecursiveDetail.class);

    private final Closer closer = Closer.create();
    private final TestingServer testingServer;
    private final JettyHttpClient httpClient = new JettyHttpClient("testing", new HttpClientConfig());
    private final InternalController internalController;
    private final JsonCodec<Detail> detailCodec;

    public IntegrationTests()
    {
        testingServer = new TestingServer(0);

        closer.register(httpClient);
        closer.register(testingServer);

        internalController = testingServer.injector().getInstance(InternalController.class);

        // must use injected object mapper which has polymorphic type handling
        ObjectMapper objectMapper = testingServer.injector().getInstance(ObjectMapper.class);
        JsonCodecFactory jsonCodecFactory = new JsonCodecFactory(() -> objectMapper);
        detailCodec = jsonCodecFactory.jsonCodec(Detail.class);
    }

    @BeforeAll
    public void setup()
    {
        URI uri = UriBuilder.fromUri(testingServer.baseUri()).path(BASE_WIDGET_PATH).build();
        IntStream.range(0, RECORD_QTY)
                .forEach(index -> {
                    ExternalWidgetId widgetId = new ExternalWidgetId(Integer.toString(index));
                    NewExternalWidget widgetDefinition = new NewExternalWidget(ExternalWidgetSize.Small, "Widget " + index, Instant.now(), ImmutableMap.of());
                    ExternalWidget externalWidget = new ExternalWidget(widgetId, new ApiResourceVersion(0), widgetDefinition);
                    call(preparePost(), uri, EXTERNAL_WIDGET_CODEC, Optional.of(externalWidget), Optional.of(EXTERNAL_WIDGET_CODEC));
                });
    }

    @AfterAll
    public void shutdown()
            throws Exception
    {
        closer.close();
    }

    @Test
    public void testVariousGets()
    {
        var pageOfResults = call(prepareGet(), UriBuilder.fromUri(testingServer.baseUri()).path(BASE_WIDGET_PATH).build(), EXTERNAL_WIDGET_LIST_CODEC);

        ApiPaginatedResult<ExternalWidget> expected = internalController.list(new ApiPagination(Optional.empty(), DEFAULT_PAGE_SIZE, Optional.empty()), new ApiFilter(Optional.empty()), new ApiFilterList(ImmutableList.of()))
                .map(InternalWidget::map);
        assertThat(pageOfResults.getStatusCode()).isEqualTo(200);
        assertThat(pageOfResults.getValue().result()).isEqualTo(expected.result());

        int randomIndex = current().nextInt(0, expected.result().size());
        ExternalWidgetId randomId = expected.result().get(randomIndex).widgetId();
        JsonResponse<ExternalWidget> randomResult = call(prepareGet(), UriBuilder.fromUri(testingServer.baseUri()).path(BASE_WIDGET_PATH).path(randomId.toString()).build(), EXTERNAL_WIDGET_CODEC);

        assertThat(randomResult.getStatusCode()).isEqualTo(200);
        assertThat(randomResult.getValue()).isEqualTo(expected.result().get(randomIndex));
    }

    @ParameterizedTest
    @MethodSource("testPaginationArguments")
    public void testPagination(int pageSize, Ordering ordering)
    {
        ApiPagination pagination = new ApiPagination(Optional.empty(), pageSize, Optional.of(ordering));

        int totalReturned = 0;
        do {
            UriBuilder uriBuilder = UriBuilder.fromUri(testingServer.baseUri())
                    .path(BASE_WIDGET_PATH)
                    .queryParam(PAGE_SIZE_QUERY_PARAMETER_NAME, pageSize);

            pagination.pageToken().ifPresent(pageToken -> uriBuilder.queryParam(PAGE_TOKEN_QUERY_PARAMETER_NAME, pageToken));
            pagination.ordering().ifPresent(order -> uriBuilder.queryParam(ORDER_BY_PARAMETER_NAME, order.toQueryValue()));

            URI uri = uriBuilder.build();
            var pageOfResults = call(prepareGet(), uri, EXTERNAL_WIDGET_LIST_CODEC);

            ApiPaginatedResult<ExternalWidget> expected = internalController.list(pagination, new ApiFilter(Optional.empty()), new ApiFilterList(ImmutableList.of()))
                    .map(InternalWidget::map);

            assertThat(pageOfResults.getStatusCode()).isEqualTo(200);
            assertThat(pageOfResults.getValue().result()).isEqualTo(expected.result());

            totalReturned += pageOfResults.getValue().result().size();
            pagination = pagination.next(pageOfResults.getValue());
        }
        while (pagination.pageToken().isPresent());

        assertThat(totalReturned).isEqualTo(RECORD_QTY);
    }

    @Test
    public void testCrud()
    {
        JsonResponse<ExternalWidget> result = call(prepareGet(), UriBuilder.fromUri(testingServer.baseUri()).path(BASE_WIDGET_PATH).path("12345").build(), EXTERNAL_WIDGET_CODEC);
        assertThat(result.getStatusCode()).isEqualTo(404);

        URI uri = UriBuilder.fromUri(testingServer.baseUri()).path(BASE_WIDGET_PATH).build();
        NewExternalWidget widgetDefinition = new NewExternalWidget(ExternalWidgetSize.Small, "Test Widget", Instant.now(), ImmutableMap.of());
        JsonResponse<ExternalWidget> newWidgetResult = call(preparePost(), uri, EXTERNAL_WIDGET_CODEC, Optional.of(widgetDefinition), Optional.of(NEW_WIDGET_CODEC));
        assertThat(newWidgetResult.getStatusCode()).isEqualTo(200);

        result = call(prepareGet(), UriBuilder.fromUri(testingServer.baseUri()).path(BASE_WIDGET_PATH).path(newWidgetResult.getValue().widgetId().toString()).build(), EXTERNAL_WIDGET_CODEC);
        assertThat(result.getStatusCode()).isEqualTo(200);

        Map<String, String> patch = ImmutableMap.of("name", "changed");
        result = call(preparePatch(), UriBuilder.fromUri(testingServer.baseUri()).path(BASE_WIDGET_PATH).path(newWidgetResult.getValue().widgetId().toString()).build(), EXTERNAL_WIDGET_CODEC, Optional.of(patch), Optional.of(MAP_CODEC));
        assertThat(result.getStatusCode()).isEqualTo(200);

        result = call(prepareGet(), UriBuilder.fromUri(testingServer.baseUri()).path(BASE_WIDGET_PATH).path(newWidgetResult.getValue().widgetId().toString()).build(), EXTERNAL_WIDGET_CODEC);
        assertThat(result.getStatusCode()).isEqualTo(200);
        assertThat(result.getValue().widgetDefinition().name()).isEqualTo("changed");

        result = call(prepareDelete(), UriBuilder.fromUri(testingServer.baseUri()).path(BASE_WIDGET_PATH).path(newWidgetResult.getValue().widgetId().toString()).build(), EXTERNAL_WIDGET_CODEC);
        assertThat(result.getStatusCode()).isEqualTo(200);
    }

    @Test
    public void testPolymorphic()
    {
        NameAndAge nameAndAge = new NameAndAge("A name", 42);
        JsonResponse<StringResult> result = call(preparePost(), UriBuilder.fromUri(testingServer.baseUri()).path(BASE_PATH).path("test").build(), STRING_RESULT_CODEC, Optional.of(nameAndAge), Optional.of(detailCodec));
        assertThat(result.getStatusCode()).isEqualTo(200);
        assertThat(result.getValue().message()).isEqualTo("Detail created: " + NameAndAge.class.getSimpleName());

        Schedule schedule = new Schedule("A schedule", Instant.now(), Instant.now().plusSeconds(60));
        result = call(preparePost(), UriBuilder.fromUri(testingServer.baseUri()).path(BASE_PATH).path("test").build(), STRING_RESULT_CODEC, Optional.of(schedule), Optional.of(detailCodec));
        assertThat(result.getStatusCode()).isEqualTo(200);
        assertThat(result.getValue().message()).isEqualTo("Detail created: " + Schedule.class.getSimpleName());

        JsonResponse<Detail> detailResult = call(prepareGet(), UriBuilder.fromUri(testingServer.baseUri()).path(BASE_PATH).path("detail").path("1").build(), detailCodec);
        assertThat(detailResult.getStatusCode()).isEqualTo(200);
        assertThat(detailResult.getValue()).isInstanceOf(NameAndAge.class);

        detailResult = call(prepareGet(), UriBuilder.fromUri(testingServer.baseUri()).path(BASE_PATH).path("detail").path("2").build(), detailCodec);
        assertThat(detailResult.getStatusCode()).isEqualTo(200);
        assertThat(detailResult.getValue()).isInstanceOf(Schedule.class);
    }

    @Test
    public void testHidden()
    {
        JsonResponse<StringResult> result = call(prepareGet(), UriBuilder.fromUri(testingServer.baseUri()).path(BASE_PATH).path("/test:hidden").build(), STRING_RESULT_CODEC);
        assertThat(result.getStatusCode()).isEqualTo(200);
        assertThat(result.getValue().message()).isEqualTo("hidden success");
    }

    @Test
    public void testStreaming()
            throws IOException
    {
        for (String type : ImmutableList.of("streamChars", "streamBytes")) {
            URI uri = UriBuilder.fromUri(testingServer.baseUri()).path(BASE_PATH).path("/widget:" + type).build();
            Request request = prepareGet().setUri(uri).build();
            try (StreamingResponse streamingResponse = httpClient.executeStreaming(request)) {
                assertThat(streamingResponse.getStatusCode()).isEqualTo(200);
                String text = new String(streamingResponse.getInputStream().readAllBytes());
                assertThat(text).isEqualTo("This is a test");
            }
        }
    }

    @Test
    public void testRecursive()
    {
        RecursiveDetail subRecursiveDetail = new RecursiveDetail("sub", ImmutableList.of());
        RecursiveDetail.Item item = new RecursiveDetail.Item(1, ImmutableList.of(subRecursiveDetail));
        RecursiveDetail recursiveDetail = new RecursiveDetail("me", ImmutableList.of(item));

        JsonResponse<StringResult> result = call(preparePost(), UriBuilder.fromUri(testingServer.baseUri()).path(BASE_PATH).path("test:recursive").build(), STRING_RESULT_CODEC, Optional.of(recursiveDetail), Optional.of(RECURSIVE_DETAIL_CODEC));
        assertThat(result.getStatusCode()).isEqualTo(200);
        assertThat(result.getValue().message()).isEqualTo("Recursive: me, sub");
    }

    private static Stream<Arguments> testPaginationArguments()
    {
        return Stream.of(1, 25, DEFAULT_PAGE_SIZE, RECORD_QTY * 2)
                .flatMap(pageSize -> Stream.of("id", "name", "size")
                        .flatMap(field -> Stream.of(ASCENDING, DESCENDING)
                                .map(sortBy -> Arguments.of(pageSize, new Ordering(field, sortBy)))));
    }

    private <T> JsonResponse<T> call(Request.Builder requestBuilder, URI uri, JsonCodec<T> responseCodec)
    {
        return call(requestBuilder, uri, responseCodec, Optional.empty(), Optional.empty());
    }

    private <T, B> JsonResponse<T> call(Request.Builder requestBuilder, URI uri, JsonCodec<T> responseCodec, Optional<B> payload, Optional<JsonCodec<B>> payloadCodec)
    {
        requestBuilder.setUri(uri);
        payload.ifPresent(body -> requestBuilder.setBodyGenerator(jsonBodyGenerator(payloadCodec.orElseThrow(), body)));
        requestBuilder.setHeader("Content-Type", "application/json");
        Request request = requestBuilder.build();

        return httpClient.execute(request, FullJsonResponseHandler.createFullJsonResponseHandler(responseCodec));
    }
}
