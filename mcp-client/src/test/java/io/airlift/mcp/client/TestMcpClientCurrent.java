package io.airlift.mcp.client;

import com.google.common.collect.ImmutableMap;
import io.airlift.http.client.Request;
import io.airlift.mcp.McpException;
import io.airlift.mcp.client.settings.MetaOnly;
import io.airlift.mcp.client.settings.ProgressToken;
import io.airlift.mcp.client.settings.RequestCache;
import io.airlift.mcp.client.settings.ResponseFilter;
import io.airlift.mcp.model.CacheableResult;
import io.airlift.mcp.model.CallToolRequest;
import io.airlift.mcp.model.ElicitResult;
import io.airlift.mcp.model.JsonRpcResponse;
import io.airlift.mcp.model.ReadResourceRequest;
import io.airlift.mcp.model.ToolResult;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static io.airlift.mcp.TestingIdentityMapper.EXPECTED_IDENTITY;
import static io.airlift.mcp.TestingIdentityMapper.IDENTITY_HEADER;
import static io.airlift.mcp.client.McpClient.mcpClient;
import static io.airlift.mcp.client.McpClientSetting.ELICITATION_ENABLED;
import static io.airlift.mcp.client.McpClientSetting.MAX_TASK_SERVICE_PERIOD;
import static io.airlift.mcp.client.McpClientSetting.MIN_TASK_SERVICE_PERIOD;
import static io.airlift.mcp.client.McpClientSetting.MODE;
import static io.airlift.mcp.client.McpClientSetting.REQUEST_CACHE_FACTORY;
import static io.airlift.mcp.client.McpConnectionSetting.MAX_INPUT_REQUEST_ROUNDS;
import static io.airlift.mcp.client.McpConnectionSetting.META;
import static io.airlift.mcp.client.McpConnectionSetting.PROGRESS_TOKEN;
import static io.airlift.mcp.client.McpConnectionSetting.REQUEST_FILTER;
import static io.airlift.mcp.client.McpConnectionSetting.RESPONSE_FILTER;
import static io.airlift.mcp.client.settings.ClientMode.LEGACY_PROTOCOL_DISABLED;
import static io.airlift.mcp.model.Constants.SKILL_INDEX_URI;
import static io.airlift.mcp.model.JsonRpcErrorCode.HEADER_MISMATCH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class TestMcpClientCurrent
        extends TestMcpClient
{
    public TestMcpClientCurrent()
    {
        super(LEGACY_PROTOCOL_DISABLED);
    }

    @Test
    public void testNonAsciiNameMatchesItsHeader()
    {
        McpClient client = mcpClient(httpClient())
                .withSetting(MODE, LEGACY_PROTOCOL_DISABLED)
                .withDefaultConnectionSetting(REQUEST_FILTER, builder -> builder.setHeader(IDENTITY_HEADER, EXPECTED_IDENTITY));

        try (McpConnection connection = client.connect(uri())) {
            // the strict server must decode the base64 Mcp-Name header before comparing it to the name, so the call
            // gets past header validation and fails only on the tool itself, which does not exist
            assertThatThrownBy(() -> connection.callTool(new CallToolRequest("café", ImmutableMap.of())))
                    .isInstanceOfSatisfying(McpException.class, exception -> {
                        assertThat(exception.errorDetail().code()).isNotEqualTo(HEADER_MISMATCH.code());
                        assertThat(exception.getMessage()).endsWith(": café");
                    });
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testConnectionsWithDifferentMetaDoNotShareCachedResults()
    {
        // responses rather than requests are counted: a request is prepared before the cache is consulted
        AtomicInteger responses = new AtomicInteger();

        // the testing server does not let its results be cached, so they are made cacheable here
        ResponseFilter cacheableResults = (_, rpcResponse) -> {
            responses.incrementAndGet();
            return rpcResponse.result()
                    .filter(Map.class::isInstance)
                    .map(result -> ImmutableMap.<String, Object>builder()
                            .putAll((Map<String, Object>) result)
                            .put("ttlMs", 60_000)
                            .put("cacheScope", "public")
                            .buildKeepingLast())
                    .map(result -> new JsonRpcResponse<Object>(rpcResponse.id(), rpcResponse.error(), Optional.of(result)))
                    .orElse(rpcResponse);
        };

        McpClient client = mcpClient(httpClient())
                .withSetting(MODE, LEGACY_PROTOCOL_DISABLED)
                .withDefaultConnectionSetting(RESPONSE_FILTER, cacheableResults)
                .withDefaultConnectionSetting(REQUEST_FILTER, builder -> builder.setHeader(IDENTITY_HEADER, EXPECTED_IDENTITY));

        try (McpConnection connection = client.connect(uri())) {
            connection.listTools();
            connection.listTools();
            assertThat(responses).hasValue(1);

            // a derived connection shares the cache, but a listing made with other metadata is not its listing
            McpConnection tenantConnection = connection.withSetting(META, new MetaOnly(ImmutableMap.of("tenant", "two")));
            tenantConnection.listTools();
            assertThat(responses).hasValue(2);
            tenantConnection.listTools();
            assertThat(responses).hasValue(2);

            // a progress token says nothing about the result, so it does not stop the listing being reused
            connection.withSetting(PROGRESS_TOKEN, new ProgressToken("token")).listTools();
            assertThat(responses).hasValue(2);
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testAnsweredTaskInputIsNotAskedAgain()
            throws InterruptedException
    {
        // a server may acknowledge tasks/update before tasks/get reflects it, so the first poll after the answer
        // is replaced with the input_required snapshot the client has just answered
        AtomicReference<Object> answeredSnapshot = new AtomicReference<>();
        AtomicBoolean replayed = new AtomicBoolean();
        ResponseFilter staleAfterAnswer = (_, rpcResponse) -> rpcResponse.result()
                .filter(Map.class::isInstance)
                .map(result -> (Map<String, Object>) result)
                .filter(task -> task.containsKey("taskId"))
                .map(task -> {
                    if ("input_required".equals(task.get("status"))) {
                        answeredSnapshot.compareAndSet(null, task);
                        return rpcResponse;
                    }
                    if ((answeredSnapshot.get() != null) && replayed.compareAndSet(false, true)) {
                        return new JsonRpcResponse<>(rpcResponse.id(), Optional.empty(), Optional.of(answeredSnapshot.get()));
                    }
                    return rpcResponse;
                })
                .orElse(rpcResponse);

        AtomicInteger handlerInvocations = new AtomicInteger();
        McpInputRequestsHandler handler = inputRequests -> {
            handlerInvocations.incrementAndGet();
            return ImmutableMap.of(inputRequests.keySet().iterator().next(), new ElicitResult(ElicitResult.Action.ACCEPT, Optional.of(ImmutableMap.of("confirm", true)), Optional.empty()));
        };

        McpTasksClient client = mcpClient(httpClient())
                .withSetting(MODE, LEGACY_PROTOCOL_DISABLED)
                .withSetting(ELICITATION_ENABLED, true)
                .withSetting(MIN_TASK_SERVICE_PERIOD, Duration.ofMillis(50))
                .withSetting(MAX_TASK_SERVICE_PERIOD, Duration.ofMillis(200))
                .withDefaultConnectionSetting(REQUEST_FILTER, builder -> builder.setHeader(IDENTITY_HEADER, EXPECTED_IDENTITY))
                .withDefaultConnectionSetting(RESPONSE_FILTER, staleAfterAnswer)
                .withTasks();

        try (McpTasksConnection connection = client.connect(uri())) {
            // a single round is all the task needs - the stale snapshot must not be counted as another one
            McpTasksConnection limited = connection.withSetting(MAX_INPUT_REQUEST_ROUNDS, 1);
            ToolResult taskResult = limited.callToolOrTask(new CallToolRequest("task_confirm", ImmutableMap.of("filename", "data.txt")));
            String result = handler.asTaskResultProcessor()
                    .process(limited, taskResult, McpMapper::optionalContentString)
                    .required();

            assertThat(result).isEqualTo("Deleted data.txt");
            assertThat(replayed).isTrue();
            // the stale snapshot must not put the same question to the user again
            assertThat(handlerInvocations).hasValue(1);
        }
    }

    @Test
    public void testInputResponsesBypassTheCache()
    {
        List<ReadResourceRequest> cachedReads = new CopyOnWriteArrayList<>();
        RequestCache recordingCache = new RequestCache()
        {
            @Override
            public <T, R extends CacheableResult<?>> R executeRequest(Request request, String mcpMethod, T mcpRequest, Class<R> resultClass, Optional<String> cursor, Supplier<R> resultSupplier)
            {
                if (mcpRequest instanceof ReadResourceRequest readResourceRequest) {
                    cachedReads.add(readResourceRequest);
                }
                return resultSupplier.get();
            }
        };

        McpClient client = mcpClient(httpClient())
                .withSetting(MODE, LEGACY_PROTOCOL_DISABLED)
                .withSetting(REQUEST_CACHE_FACTORY, _ -> recordingCache)
                .withDefaultConnectionSetting(REQUEST_FILTER, builder -> builder.setHeader(IDENTITY_HEADER, EXPECTED_IDENTITY));

        try (McpConnection connection = client.connect(uri())) {
            // a plain read goes through the cache
            connection.readResource(new ReadResourceRequest(SKILL_INDEX_URI));
            assertThat(cachedReads).hasSize(1);

            // a retry carrying input responses depends on inputs the cache key does not hold, so the cache must
            // neither serve nor store its result
            connection.readResource(new ReadResourceRequest(SKILL_INDEX_URI).withInputResponses(Optional.empty(), ImmutableMap.of("answer", "yes")));
            assertThat(cachedReads).hasSize(1);
        }
    }
}
