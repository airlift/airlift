package io.airlift.mcp.client.legacy;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.airlift.mcp.McpException;
import io.airlift.mcp.client.McpClient;
import io.airlift.mcp.client.McpClientTestBase;
import io.airlift.mcp.client.McpConnection;
import io.airlift.mcp.client.settings.RequestFilter;
import io.airlift.mcp.client.settings.ResponseFilter;
import io.airlift.mcp.model.CallToolRequest;
import io.airlift.mcp.model.JsonRpcErrorDetail;
import io.airlift.mcp.model.JsonRpcResponse;
import io.airlift.mcp.model.SubscriptionFilter;
import io.airlift.mcp.model.SubscriptionNotifications;
import io.airlift.mcp.model.UnsupportedProtocolVersionError;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static io.airlift.mcp.TestingIdentityMapper.EXPECTED_IDENTITY;
import static io.airlift.mcp.TestingIdentityMapper.IDENTITY_HEADER;
import static io.airlift.mcp.client.McpClient.mcpClient;
import static io.airlift.mcp.client.McpClientSetting.MODE;
import static io.airlift.mcp.client.McpConnectionSetting.MAX_INPUT_REQUEST_ROUNDS;
import static io.airlift.mcp.client.McpConnectionSetting.REQUEST_FILTER;
import static io.airlift.mcp.client.McpConnectionSetting.RESPONSE_FILTER;
import static io.airlift.mcp.client.McpMapper.requireContentString;
import static io.airlift.mcp.client.settings.ClientMode.LEGACY_PROTOCOL_OPTIONAL;
import static io.airlift.mcp.model.JsonRpcErrorCode.INTERNAL_ERROR;
import static io.airlift.mcp.model.JsonRpcErrorCode.INVALID_PARAMS;
import static io.airlift.mcp.model.JsonRpcErrorCode.INVALID_REQUEST;
import static io.airlift.mcp.model.JsonRpcErrorCode.UNSUPPORTED_PROTOCOL;
import static io.airlift.mcp.model.Protocol.PROTOCOL_MCP_2025_11_25;
import static io.airlift.mcp.model.Protocol.PROTOCOL_MCP_2026_07_28;
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
            SubscriptionFilter filter = new SubscriptionFilter(Optional.of(true), Optional.of(true), Optional.of(true), Optional.empty());
            try (AutoCloseable subscription = connection.subscribe(new SubscriptionNotifications(filter, Optional.empty()))) {
                assertThat(subscription).isNotNull();
                assertThat(rejected).isTrue();

                // the fallback happened, so the subscription was opened against the legacy connection
                assertThat(connection.serverDiscover().supportedVersions()).containsExactly(PROTOCOL_MCP_2025_11_25.value());
            }
        }
    }

    private McpClient client(AtomicBoolean rejected)
    {
        return client(rejected, new AtomicInteger());
    }

    private McpClient client(AtomicBoolean rejected, AtomicInteger requests)
    {
        RequestFilter countingIdentityFilter = builder -> {
            requests.incrementAndGet();
            return builder.setHeader(IDENTITY_HEADER, EXPECTED_IDENTITY);
        };

        return mcpClient(httpClient())
                .withSetting(MODE, LEGACY_PROTOCOL_OPTIONAL)
                .withDefaultConnectionSetting(REQUEST_FILTER, countingIdentityFilter)
                .withDefaultConnectionSetting(RESPONSE_FILTER, rejectFirstResponse(rejected));
    }
}
