package io.airlift.mcp.client.legacy;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.airlift.http.client.HeaderName;
import io.airlift.http.client.HttpClientConfig;
import io.airlift.http.client.HttpRequestFilter;
import io.airlift.http.client.Request;
import io.airlift.http.client.StaticBodyGenerator;
import io.airlift.http.client.jetty.JettyHttpClient;
import io.airlift.mcp.McpException;
import io.airlift.mcp.client.McpClient;
import io.airlift.mcp.client.McpClientTestBase;
import io.airlift.mcp.client.McpConnection;
import io.airlift.mcp.client.McpTasksClient;
import io.airlift.mcp.client.settings.RequestFilter;
import io.airlift.mcp.client.settings.ResponseFilter;
import io.airlift.mcp.model.CallToolRequest;
import io.airlift.mcp.model.CallToolResult;
import io.airlift.mcp.model.JsonRpcErrorDetail;
import io.airlift.mcp.model.JsonRpcResponse;
import io.airlift.mcp.model.SubscriptionFilter;
import io.airlift.mcp.model.SubscriptionNotifications;
import io.airlift.mcp.model.UnsupportedProtocolVersionError;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static io.airlift.mcp.TestingIdentityMapper.EXPECTED_IDENTITY;
import static io.airlift.mcp.TestingIdentityMapper.IDENTITY_HEADER;
import static io.airlift.mcp.client.McpClient.mcpClient;
import static io.airlift.mcp.client.McpClientSetting.MODE;
import static io.airlift.mcp.client.McpConnectionSetting.MAX_INPUT_REQUEST_ROUNDS;
import static io.airlift.mcp.client.McpConnectionSetting.REQUEST_FILTER;
import static io.airlift.mcp.client.McpConnectionSetting.RESPONSE_FILTER;
import static io.airlift.mcp.client.McpMapper.jsonMapper;
import static io.airlift.mcp.client.McpMapper.requireContentString;
import static io.airlift.mcp.client.settings.ClientMode.LEGACY_PROTOCOL_OPTIONAL;
import static io.airlift.mcp.model.Constants.EXTENSION_TASKS;
import static io.airlift.mcp.model.Constants.HEADER_PROTOCOL_VERSION;
import static io.airlift.mcp.model.Constants.METADATA_CLIENT_CAPABILITIES;
import static io.airlift.mcp.model.Constants.METADATA_PROTOCOL_VERSION;
import static io.airlift.mcp.model.JsonRpcErrorCode.INTERNAL_ERROR;
import static io.airlift.mcp.model.JsonRpcErrorCode.INVALID_PARAMS;
import static io.airlift.mcp.model.JsonRpcErrorCode.INVALID_REQUEST;
import static io.airlift.mcp.model.JsonRpcErrorCode.UNSUPPORTED_PROTOCOL;
import static io.airlift.mcp.model.Protocol.PROTOCOL_MCP_2025_11_25;
import static io.airlift.mcp.model.Protocol.PROTOCOL_MCP_2026_07_28;
import static java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

@TestInstance(PER_CLASS)
public class TestLegacyOptionalFallback
        extends McpClientTestBase
{
    /**
     * The testing server speaks the current protocol, so rejecting the first response is what makes the fallback
     * path reachable at all - after that the filter gets out of the way and the legacy retry runs against the real
     * server.
     */
    private static ResponseFilter rejectFirstResponse(AtomicBoolean rejected)
    {
        return (_, rpcResponse) -> {
            if (rejected.compareAndSet(false, true)) {
                UnsupportedProtocolVersionError error = new UnsupportedProtocolVersionError(ImmutableList.of(PROTOCOL_MCP_2025_11_25.value()), PROTOCOL_MCP_2026_07_28.value());
                return new JsonRpcResponse<>(rpcResponse.id(), Optional.of(new JsonRpcErrorDetail(UNSUPPORTED_PROTOCOL, "Unsupported protocol version", error)), Optional.empty());
            }
            return rpcResponse;
        };
    }

    @Test
    public void testFallbackRetriesOnTheLegacyProtocol()
    {
        AtomicBoolean rejected = new AtomicBoolean();

        try (McpConnection connection = client(rejected).connect(uri())) {
            // the first request is rejected as a protocol mismatch - the client must fall back and retry
            assertThat(connection.listTools().tools()).isNotEmpty();
            assertThat(rejected).isTrue();
            assertThat(connection.serverDiscover().supportedVersions()).containsExactly(PROTOCOL_MCP_2025_11_25.value());
        }
    }

    @Test
    public void testFallbackDoesNotReHandshakePerRequest()
    {
        AtomicBoolean rejected = new AtomicBoolean();
        AtomicInteger requests = new AtomicInteger();

        try (McpConnection connection = client(rejected, requests).connect(uri())) {
            assertThat(connection.listTools().tools()).isNotEmpty();
            int afterFallback = requests.get();

            // a second request on the legacy connection is one more request - not another initialize handshake
            assertThat(connection.listTools().tools()).isNotEmpty();
            assertThat(requests.get() - afterFallback).isEqualTo(1);
        }
    }

    @Test
    public void testReadingASettingDoesNotDecideTheProtocol()
    {
        AtomicBoolean rejected = new AtomicBoolean();

        try (McpConnection connection = client(rejected).connect(uri())) {
            // a local read must not conclude the probe - the server has not been contacted yet
            connection.setting(REQUEST_FILTER);

            assertThat(connection.listTools().tools()).isNotEmpty();
            assertThat(connection.serverDiscover().supportedVersions()).containsExactly(PROTOCOL_MCP_2025_11_25.value());
        }
    }

    @Test
    public void testDerivedConnectionWorksAfterFallbackOnTheOriginal()
    {
        AtomicBoolean rejected = new AtomicBoolean();

        try (McpConnection connection = client(rejected).connect(uri())) {
            // derived while the protocol is still undecided, so it has no legacy connection of its own yet
            McpConnection derived = connection.withSetting(MAX_INPUT_REQUEST_ROUNDS, 3);

            // the original falls back ...
            assertThat(connection.listTools().tools()).isNotEmpty();

            // ... and the derived connection must follow it onto the legacy protocol, keeping its own override
            assertThat(derived.listTools().tools()).isNotEmpty();
            assertThat(derived.setting(MAX_INPUT_REQUEST_ROUNDS)).isEqualTo(3);
        }
    }

    @Test
    public void testOriginalConnectionWorksAfterFallbackOnADerivedConnection()
    {
        AtomicBoolean rejected = new AtomicBoolean();

        try (McpConnection connection = client(rejected).connect(uri())) {
            McpConnection derived = connection.withSetting(MAX_INPUT_REQUEST_ROUNDS, 3);

            // the derived connection falls back ...
            assertThat(derived.listTools().tools()).isNotEmpty();

            // ... and the connection it came from must follow it, without inheriting its override
            assertThat(connection.listTools().tools()).isNotEmpty();
            assertThat(connection.setting(MAX_INPUT_REQUEST_ROUNDS)).isEqualTo(10);
        }
    }

    @Test
    public void testDerivedOverrideReachesTheWireAfterFallback()
    {
        AtomicBoolean rejected = new AtomicBoolean();
        AtomicInteger overrideFilterCalls = new AtomicInteger();
        RequestFilter identityFilter = builder -> builder.setHeader(IDENTITY_HEADER, EXPECTED_IDENTITY);
        RequestFilter overrideFilter = identityFilter.andThen(builder -> {
            overrideFilterCalls.incrementAndGet();
            return builder;
        });

        try (McpConnection connection = client(rejected).connect(uri())) {
            McpConnection derived = connection.withSetting(REQUEST_FILTER, overrideFilter);

            // the original falls back, so the derived connection never probed and never sent anything
            assertThat(connection.listTools().tools()).isNotEmpty();
            assertThat(overrideFilterCalls).hasValue(0);

            // the derived connection's override must be applied to the legacy connection it inherits
            assertThat(derived.listTools().tools()).isNotEmpty();
            assertThat(overrideFilterCalls).hasValue(1);
        }
    }

    @Test
    public void testDerivedConnectionFollowsTheFallback()
    {
        AtomicBoolean rejected = new AtomicBoolean();

        try (McpConnection connection = client(rejected).connect(uri())) {
            // derived while the protocol is still undecided
            McpConnection derived = connection.withSetting(MAX_INPUT_REQUEST_ROUNDS, 3);

            assertThat(connection.listTools().tools()).isNotEmpty();
            assertThat(connection.serverDiscover().supportedVersions()).containsExactly(PROTOCOL_MCP_2025_11_25.value());

            // the derived connection has to be on the legacy protocol too - it must not still be speaking the
            // current one against a server that has already rejected it
            assertThat(derived.serverDiscover().supportedVersions()).containsExactly(PROTOCOL_MCP_2025_11_25.value());
        }
    }

    @Test
    public void testEstablishedConnectionIsNotDraggedIntoTheLegacyProtocol()
    {
        AtomicBoolean armed = new AtomicBoolean();
        AtomicInteger requests = new AtomicInteger();

        ResponseFilter rejectOnceWhenArmed = (_, rpcResponse) -> {
            if (armed.compareAndSet(true, false)) {
                return new JsonRpcResponse<>(rpcResponse.id(), Optional.of(new JsonRpcErrorDetail(INVALID_REQUEST, "not a protocol problem")), Optional.empty());
            }
            return rpcResponse;
        };

        RequestFilter countingIdentityFilter = builder -> {
            requests.incrementAndGet();
            return builder.setHeader(IDENTITY_HEADER, EXPECTED_IDENTITY);
        };

        McpClient client = mcpClient(httpClient())
                .withSetting(MODE, LEGACY_PROTOCOL_OPTIONAL)
                .withDefaultConnectionSetting(REQUEST_FILTER, countingIdentityFilter)
                .withDefaultConnectionSetting(RESPONSE_FILTER, rejectOnceWhenArmed);

        CallToolRequest add = new CallToolRequest("add", ImmutableMap.of("a", 1, "b", 2));

        try (McpConnection connection = client.connect(uri())) {
            // the first successful request settles the protocol
            assertThat(requireContentString(connection.callTool(add))).isEqualTo("3");
            assertThat(connection.serverDiscover().supportedVersions()).contains(PROTOCOL_MCP_2026_07_28.value());

            // a later INVALID_REQUEST is an application error, not a protocol signal: it must surface as-is,
            // the request must not be re-sent, and the connection must not be rebuilt on the legacy protocol
            armed.set(true);
            int before = requests.get();
            assertThatThrownBy(() -> connection.callTool(add))
                    .isInstanceOf(McpException.class)
                    .hasMessageContaining("not a protocol problem");
            assertThat(requests.get() - before).isEqualTo(1);
            assertThat(connection.serverDiscover().supportedVersions()).contains(PROTOCOL_MCP_2026_07_28.value());
        }
    }

    @Test
    public void testUnsupportedProtocolWithNoLegacyOptionSurfacesTheError()
    {
        // the server rejects the current protocol and does not offer the legacy one either, so there is nothing
        // to fall back to - the protocol error itself has to reach the caller
        ResponseFilter rejectWithNoLegacyOption = (_, rpcResponse) -> {
            UnsupportedProtocolVersionError error = new UnsupportedProtocolVersionError(ImmutableList.of("2099-01-01"), PROTOCOL_MCP_2026_07_28.value());
            return new JsonRpcResponse<>(rpcResponse.id(), Optional.of(new JsonRpcErrorDetail(UNSUPPORTED_PROTOCOL, "Unsupported protocol version", error)), Optional.empty());
        };

        McpClient client = mcpClient(httpClient())
                .withSetting(MODE, LEGACY_PROTOCOL_OPTIONAL)
                .withDefaultConnectionSetting(REQUEST_FILTER, builder -> builder.setHeader(IDENTITY_HEADER, EXPECTED_IDENTITY))
                .withDefaultConnectionSetting(RESPONSE_FILTER, rejectWithNoLegacyOption);

        try (McpConnection connection = client.connect(uri())) {
            assertThatThrownBy(connection::listTools)
                    .isInstanceOf(McpException.class)
                    .hasMessageContaining("Unsupported protocol version");
        }
    }

    @Test
    public void testAFailedLegacyHandshakeLeavesTheProtocolUndecided()
    {
        AtomicInteger responses = new AtomicInteger();
        ResponseFilter rejectProtocolThenHandshake = (_, rpcResponse) -> {
            JsonRpcErrorDetail errorDetail = switch (responses.incrementAndGet()) {
                // the current protocol is rejected in favour of the legacy one ...
                case 1 -> new JsonRpcErrorDetail(
                        UNSUPPORTED_PROTOCOL,
                        "Unsupported protocol version",
                        new UnsupportedProtocolVersionError(ImmutableList.of(PROTOCOL_MCP_2025_11_25.value()), PROTOCOL_MCP_2026_07_28.value()));

                // ... but the legacy handshake that follows then fails. Only "initialize" reaches this filter -
                // the notifications the handshake sends have Void results and never get here
                case 2 -> new JsonRpcErrorDetail(INTERNAL_ERROR, "handshake failed");

                default -> null;
            };

            return (errorDetail == null) ? rpcResponse : new JsonRpcResponse<>(rpcResponse.id(), Optional.of(errorDetail), Optional.empty());
        };

        McpClient client = mcpClient(httpClient())
                .withSetting(MODE, LEGACY_PROTOCOL_OPTIONAL)
                .withDefaultConnectionSetting(REQUEST_FILTER, builder -> builder.setHeader(IDENTITY_HEADER, EXPECTED_IDENTITY))
                .withDefaultConnectionSetting(RESPONSE_FILTER, rejectProtocolThenHandshake);

        try (McpConnection connection = client.connect(uri())) {
            // the fallback is attempted, the handshake fails, and that failure is what the caller sees
            assertThatThrownBy(connection::listTools)
                    .isInstanceOf(McpException.class)
                    .hasMessageContaining("handshake failed");

            // the protocol was never decided, so the next call probes again - it is not stranded on a legacy
            // connection that could not be built
            assertThat(connection.listTools().tools()).isNotEmpty();
            assertThat(connection.serverDiscover().supportedVersions()).contains(PROTOCOL_MCP_2026_07_28.value());
        }
    }

    @Test
    public void testAFailedFirstRequestStillSettlesTheProtocol()
    {
        AtomicInteger responses = new AtomicInteger();
        AtomicInteger requests = new AtomicInteger();

        ResponseFilter injectErrors = (_, rpcResponse) -> {
            JsonRpcErrorDetail errorDetail = switch (responses.incrementAndGet()) {
                // a business error - it says nothing about the protocol
                case 1 -> new JsonRpcErrorDetail(INVALID_PARAMS, "tool failed");

                // protocol-shaped, but by now the protocol question is settled
                case 2 -> new JsonRpcErrorDetail(INVALID_REQUEST, "cannot deserialize");

                default -> null;
            };

            return (errorDetail == null) ? rpcResponse : new JsonRpcResponse<>(rpcResponse.id(), Optional.of(errorDetail), Optional.empty());
        };

        RequestFilter countingIdentityFilter = builder -> {
            requests.incrementAndGet();
            return builder.setHeader(IDENTITY_HEADER, EXPECTED_IDENTITY);
        };

        McpClient client = mcpClient(httpClient())
                .withSetting(MODE, LEGACY_PROTOCOL_OPTIONAL)
                .withDefaultConnectionSetting(REQUEST_FILTER, countingIdentityFilter)
                .withDefaultConnectionSetting(RESPONSE_FILTER, injectErrors);

        try (McpConnection connection = client.connect(uri())) {
            // a business error on the very first request must not downgrade the protocol, and above all must not
            // re-execute the request - it may not be idempotent
            int before = requests.get();
            assertThatThrownBy(connection::listTools)
                    .isInstanceOf(McpException.class)
                    .hasMessageContaining("tool failed");
            assertThat(requests.get() - before).isEqualTo(1);

            // the protocol question is settled by that first exchange, so a later protocol-shaped failure is
            // just a failure
            before = requests.get();
            assertThatThrownBy(connection::listTools)
                    .isInstanceOf(McpException.class)
                    .hasMessageContaining("cannot deserialize");
            assertThat(requests.get() - before).isEqualTo(1);

            assertThat(connection.serverDiscover().supportedVersions()).contains(PROTOCOL_MCP_2026_07_28.value());
        }
    }

    @Test
    public void testSubscribeFirstStillResolvesTheProtocol()
            throws Exception
    {
        AtomicBoolean rejected = new AtomicBoolean();

        try (McpConnection connection = client(rejected).connect(uri())) {
            // subscribing is the first thing this connection does. A listen stream is driven on a background
            // thread, so its outcome cannot serve as the protocol probe - the protocol has to be settled by a
            // request whose result is actually observed, or a legacy only server would never be detected
            SubscriptionFilter filter = new SubscriptionFilter(Optional.of(true), Optional.of(true), Optional.of(true), Optional.empty(), Optional.empty());
            try (AutoCloseable subscription = connection.subscribe(new SubscriptionNotifications(filter, Optional.empty()))) {
                assertThat(subscription).isNotNull();
                assertThat(rejected).isTrue();

                // the fallback happened, so the subscription was opened against the legacy connection
                assertThat(connection.serverDiscover().supportedVersions()).containsExactly(PROTOCOL_MCP_2025_11_25.value());
            }
        }
    }

    @Test
    public void testUnreadableFirstResponseIsNotResentOnTheLegacyProtocol()
    {
        AtomicBoolean garbled = new AtomicBoolean();
        AtomicInteger requests = new AtomicInteger();

        // the server ran the tool, but its result cannot be read on this side
        ResponseFilter garbleFirstResponse = (_, rpcResponse) -> {
            if (garbled.compareAndSet(false, true)) {
                return new JsonRpcResponse<>(rpcResponse.id(), Optional.empty(), Optional.of("not a result"));
            }
            return rpcResponse;
        };

        McpClient client = mcpClient(httpClient())
                .withSetting(MODE, LEGACY_PROTOCOL_OPTIONAL)
                .withDefaultConnectionSetting(REQUEST_FILTER, countingIdentityFilter(requests))
                .withDefaultConnectionSetting(RESPONSE_FILTER, garbleFirstResponse);

        CallToolRequest add = new CallToolRequest("add", ImmutableMap.of("a", 1, "b", 2));

        try (McpConnection connection = client.connect(uri())) {
            // a failure on this side says nothing about the protocol - the tool must not be run a second time
            assertThatThrownBy(() -> connection.callTool(add))
                    .isInstanceOf(McpException.class);
            assertThat(requests.get()).isEqualTo(1);

            // and the connection stays on the current protocol
            assertThat(requireContentString(connection.callTool(add))).isEqualTo("3");
            assertThat(connection.serverDiscover().supportedVersions()).contains(PROTOCOL_MCP_2026_07_28.value());
        }
    }

    @Test
    public void testUndecidedFirstFailureLeavesTheFallbackAvailable()
    {
        AtomicInteger responses = new AtomicInteger();

        // the first request is lost, and the next one is rejected as a protocol mismatch
        ResponseFilter dropThenReject = (_, rpcResponse) -> switch (responses.getAndIncrement()) {
            case 0 -> throw new UncheckedIOException(new IOException("connection reset"));
            case 1 -> {
                UnsupportedProtocolVersionError error = new UnsupportedProtocolVersionError(ImmutableList.of(PROTOCOL_MCP_2025_11_25.value()), PROTOCOL_MCP_2026_07_28.value());
                yield new JsonRpcResponse<>(rpcResponse.id(), Optional.of(new JsonRpcErrorDetail(UNSUPPORTED_PROTOCOL, "Unsupported protocol version", error)), Optional.empty());
            }
            default -> rpcResponse;
        };

        McpClient client = mcpClient(httpClient())
                .withSetting(MODE, LEGACY_PROTOCOL_OPTIONAL)
                .withDefaultConnectionSetting(REQUEST_FILTER, countingIdentityFilter(new AtomicInteger()))
                .withDefaultConnectionSetting(RESPONSE_FILTER, dropThenReject);

        try (McpConnection connection = client.connect(uri())) {
            // the lost request says nothing about the protocol, so it must not settle it
            assertThatThrownBy(connection::listTools)
                    .isInstanceOf(McpException.class)
                    .hasMessageContaining("connection reset");

            // the next request still gets to fall back
            assertThat(connection.listTools().tools()).isNotEmpty();
            assertThat(connection.serverDiscover().supportedVersions()).containsExactly(PROTOCOL_MCP_2025_11_25.value());
        }
    }

    @Test
    public void testFallbackRequestsDeclareTheLegacyProtocol()
            throws Exception
    {
        List<Request> sent = new CopyOnWriteArrayList<>();
        HttpRequestFilter recordingFilter = request -> {
            sent.add(request);
            return request;
        };

        // tasks are enabled, so the current protocol declares the Tasks extension - the legacy protocol must not
        try (JettyHttpClient recordingHttpClient = new JettyHttpClient("recording", new HttpClientConfig(), ImmutableList.of(recordingFilter))) {
            McpTasksClient client = mcpClient(recordingHttpClient)
                    .withSetting(MODE, LEGACY_PROTOCOL_OPTIONAL)
                    .withDefaultConnectionSetting(REQUEST_FILTER, countingIdentityFilter(new AtomicInteger()))
                    .withDefaultConnectionSetting(RESPONSE_FILTER, rejectFirstResponse(new AtomicBoolean()))
                    .withTasks();

            try (McpConnection connection = client.connect(uri())) {
                assertThat(connection.listTools().tools()).isNotEmpty();
            }
        }

        int legacyRequests = 0;
        for (Request request : sent) {
            if (!(request.getBodyGenerator() instanceof StaticBodyGenerator body)) {
                continue;
            }

            // the protocol version header must match the one the request declares in _meta
            String protocolVersion = request.getHeader(HeaderName.of(HEADER_PROTOCOL_VERSION));
            JsonNode meta = jsonMapper().readTree(body.getBody()).path("params").path("_meta");
            assertThat(meta.path(METADATA_PROTOCOL_VERSION).asText()).isEqualTo(protocolVersion);

            if (protocolVersion.equals(PROTOCOL_MCP_2025_11_25.value())) {
                legacyRequests++;
                assertThat(meta.path(METADATA_CLIENT_CAPABILITIES).path("extensions").has(EXTENSION_TASKS)).isFalse();
            }
        }
        assertThat(legacyRequests).isPositive();
    }

    @Test
    public void testSlowFirstRequestDoesNotHoldUpOthers()
            throws Exception
    {
        // the first response is held back, as if the first request were a slow tool call
        CountDownLatch firstResponseHeld = new CountDownLatch(1);
        CountDownLatch releaseFirstResponse = new CountDownLatch(1);
        AtomicBoolean held = new AtomicBoolean();
        ResponseFilter holdFirstResponse = (_, rpcResponse) -> {
            if (held.compareAndSet(false, true)) {
                firstResponseHeld.countDown();
                try {
                    releaseFirstResponse.await();
                }
                catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            return rpcResponse;
        };

        McpClient client = mcpClient(httpClient())
                .withSetting(MODE, LEGACY_PROTOCOL_OPTIONAL)
                .withDefaultConnectionSetting(REQUEST_FILTER, countingIdentityFilter(new AtomicInteger()))
                .withDefaultConnectionSetting(RESPONSE_FILTER, holdFirstResponse);

        try (McpConnection connection = client.connect(uri());
                ExecutorService executor = newVirtualThreadPerTaskExecutor()) {
            try {
                Future<CallToolResult> slowCall = executor.submit(() -> connection.callTool(new CallToolRequest("add", ImmutableMap.of("a", 1, "b", 2))));
                assertThat(firstResponseHeld.await(10, SECONDS)).isTrue();

                // while the protocol is still undecided and the first request is out, another request completes
                assertThat(executor.submit(() -> connection.listTools()).get(5, SECONDS).tools()).isNotEmpty();

                releaseFirstResponse.countDown();
                assertThat(requireContentString(slowCall.get(10, SECONDS))).isEqualTo("3");
            }
            finally {
                // the held request has to finish before the executor closes, which waits for it
                releaseFirstResponse.countDown();
            }
        }
    }

    private McpClient client(AtomicBoolean rejected)
    {
        return client(rejected, new AtomicInteger());
    }

    private McpClient client(AtomicBoolean rejected, AtomicInteger requests)
    {
        return mcpClient(httpClient())
                .withSetting(MODE, LEGACY_PROTOCOL_OPTIONAL)
                .withDefaultConnectionSetting(REQUEST_FILTER, countingIdentityFilter(requests))
                .withDefaultConnectionSetting(RESPONSE_FILTER, rejectFirstResponse(rejected));
    }

    private static RequestFilter countingIdentityFilter(AtomicInteger requests)
    {
        return builder -> {
            requests.incrementAndGet();
            return builder.setHeader(IDENTITY_HEADER, EXPECTED_IDENTITY);
        };
    }
}
