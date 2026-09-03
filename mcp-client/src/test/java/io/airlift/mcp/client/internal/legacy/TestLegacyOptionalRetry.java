package io.airlift.mcp.client.internal.legacy;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import io.airlift.http.client.UnexpectedResponseException;
import io.airlift.mcp.McpException;
import io.airlift.mcp.model.JsonRpcErrorDetail;
import io.airlift.mcp.model.UnsupportedProtocolVersionError;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static io.airlift.http.client.Request.Builder.preparePost;
import static io.airlift.mcp.client.internal.legacy.LegacyOptionalSharedState.indicatesLegacyProtocol;
import static io.airlift.mcp.model.JsonRpcErrorCode.INVALID_PARAMS;
import static io.airlift.mcp.model.JsonRpcErrorCode.INVALID_REQUEST;
import static io.airlift.mcp.model.JsonRpcErrorCode.UNSUPPORTED_PROTOCOL;
import static io.airlift.mcp.model.Protocol.PROTOCOL_MCP_2025_11_25;
import static io.airlift.mcp.model.Protocol.PROTOCOL_MCP_2026_07_28;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Whether a failure means "this server does not speak the current protocol". Whether that is acted on is a
 * separate question - only the first request of a connection may act on it - and is covered by
 * {@code TestLegacyOptionalFallback}.
 */
public class TestLegacyOptionalRetry
{
    @Test
    public void testHttpBadRequestIndicatesLegacyProtocol()
    {
        // a server that does not understand the current protocol at the transport level
        assertThat(indicatesLegacyProtocol(badRequestException())).isTrue();
    }

    @Test
    public void testInvalidRequestIndicatesLegacyProtocol()
    {
        assertThat(indicatesLegacyProtocol(new McpException(new JsonRpcErrorDetail(INVALID_REQUEST, "cannot deserialize")))).isTrue();
    }

    @Test
    public void testUnsupportedProtocolOfferingTheLegacyProtocolIndicatesLegacyProtocol()
    {
        assertThat(indicatesLegacyProtocol(unsupportedProtocolException(PROTOCOL_MCP_2025_11_25.value()))).isTrue();
    }

    @Test
    public void testUnsupportedProtocolWithoutTheLegacyProtocolDoesNotIndicateIt()
    {
        // the server rejected the current protocol and cannot speak the legacy one either - there is nothing to
        // fall back to, so the error has to surface
        assertThat(indicatesLegacyProtocol(unsupportedProtocolException("2099-01-01"))).isFalse();
    }

    @Test
    public void testUnsupportedProtocolWithUnreadableDataIndicatesLegacyProtocol()
    {
        // the server did not say which versions it supports, so the legacy protocol is worth trying
        McpException mcpException = new McpException(new JsonRpcErrorDetail(UNSUPPORTED_PROTOCOL, "Unsupported protocol version", "not a protocol error object"));
        assertThat(indicatesLegacyProtocol(mcpException)).isTrue();
    }

    @Test
    public void testOtherErrorsDoNotIndicateLegacyProtocol()
    {
        // a business error says nothing about the protocol
        assertThat(indicatesLegacyProtocol(new McpException(new JsonRpcErrorDetail(INVALID_PARAMS, "tool failed")))).isFalse();
    }

    private static McpException unsupportedProtocolException(String supportedVersion)
    {
        UnsupportedProtocolVersionError error = new UnsupportedProtocolVersionError(ImmutableList.of(supportedVersion), PROTOCOL_MCP_2026_07_28.value());
        return new McpException(new JsonRpcErrorDetail(UNSUPPORTED_PROTOCOL, "Unsupported protocol version", error));
    }

    private static McpException badRequestException()
    {
        UnexpectedResponseException cause = new UnexpectedResponseException(
                "Bad Request",
                preparePost().setUri(URI.create("http://localhost:1/mcp")).build(),
                400,
                ImmutableListMultimap.of());
        return new McpException(cause, new JsonRpcErrorDetail(INVALID_PARAMS, "Bad Request"));
    }
}
