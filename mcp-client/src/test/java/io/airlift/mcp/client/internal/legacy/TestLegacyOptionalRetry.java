package io.airlift.mcp.client.internal.legacy;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import io.airlift.http.client.UnexpectedResponseException;
import io.airlift.mcp.McpException;
import io.airlift.mcp.client.internal.ErrorResponseException;
import io.airlift.mcp.client.settings.StandardExceptionMapper;
import io.airlift.mcp.model.JsonRpcErrorCode;
import io.airlift.mcp.model.JsonRpcErrorDetail;
import io.airlift.mcp.model.UnsupportedProtocolVersionError;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;

import static io.airlift.http.client.Request.Builder.preparePost;
import static io.airlift.mcp.client.internal.legacy.LegacyOptionalSharedState.State.CURRENT;
import static io.airlift.mcp.client.internal.legacy.LegacyOptionalSharedState.State.LEGACY;
import static io.airlift.mcp.client.internal.legacy.LegacyOptionalSharedState.resolveProtocol;
import static io.airlift.mcp.model.JsonRpcErrorCode.HEADER_MISMATCH;
import static io.airlift.mcp.model.JsonRpcErrorCode.INVALID_PARAMS;
import static io.airlift.mcp.model.JsonRpcErrorCode.INVALID_REQUEST;
import static io.airlift.mcp.model.JsonRpcErrorCode.MISSING_REQUIRED_CLIENT_CAPABILITY;
import static io.airlift.mcp.model.JsonRpcErrorCode.UNSUPPORTED_PROTOCOL;
import static io.airlift.mcp.model.Protocol.PROTOCOL_MCP_2025_11_25;
import static io.airlift.mcp.model.Protocol.PROTOCOL_MCP_2026_07_28;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Which protocol a failure shows the server to speak, if any. Whether that is acted on is a separate question - only
 * the first request of a connection may act on it - and is covered by {@code TestLegacyOptionalFallback}.
 */
public class TestLegacyOptionalRetry
{
    @Test
    public void testHttpBadRequestIndicatesLegacyProtocol()
    {
        // a server that does not understand the current protocol at the transport level
        assertThat(resolveProtocol(unexpectedResponse(400))).contains(LEGACY);
        assertThat(resolveProtocol(unexpectedResponse(404))).contains(LEGACY);
    }

    @Test
    public void testJsonRpcErrorInBadRequestIndicatesLegacyProtocol()
    {
        assertThat(resolveProtocol(errorResponse(400, INVALID_REQUEST))).contains(LEGACY);
    }

    @Test
    public void testUnsupportedProtocolOfferingTheLegacyProtocolIndicatesLegacyProtocol()
    {
        assertThat(resolveProtocol(unsupportedProtocolException(PROTOCOL_MCP_2025_11_25.value()))).contains(LEGACY);
    }

    @Test
    public void testUnsupportedProtocolWithoutTheLegacyProtocolSettlesTheCurrentProtocol()
    {
        // the server rejected the current protocol and cannot speak the legacy one either - there is nothing to
        // fall back to, so the error has to surface
        assertThat(resolveProtocol(unsupportedProtocolException("2099-01-01"))).contains(CURRENT);
    }

    @Test
    public void testUnsupportedProtocolWithUnreadableDataIndicatesLegacyProtocol()
    {
        // the server did not say which versions it supports, so the legacy protocol is worth trying
        McpException mcpException = new McpException(new JsonRpcErrorDetail(UNSUPPORTED_PROTOCOL, "Unsupported protocol version", "not a protocol error object"));
        assertThat(resolveProtocol(mcpException)).contains(LEGACY);
    }

    @Test
    public void testCurrentProtocolErrorsSettleTheCurrentProtocol()
    {
        // only a server speaking the current protocol sends these, even with a 400
        assertThat(resolveProtocol(errorResponse(400, HEADER_MISMATCH))).contains(CURRENT);
        assertThat(resolveProtocol(errorResponse(400, MISSING_REQUIRED_CLIENT_CAPABILITY))).contains(CURRENT);
    }

    @Test
    public void testAnsweredErrorsSettleTheCurrentProtocol()
    {
        // a business error answered by the server says it accepted the current protocol
        assertThat(resolveProtocol(errorResponse(200, INVALID_PARAMS))).contains(CURRENT);
        assertThat(resolveProtocol(errorResponse(200, INVALID_REQUEST))).contains(CURRENT);
    }

    @Test
    public void testLocalFailuresDoNotDecideTheProtocol()
    {
        // a response that could not be read is mapped to INVALID_REQUEST - the server may already have acted on the
        // request, so it must not be sent again
        StandardExceptionMapper exceptionMapper = new StandardExceptionMapper();
        assertThat(resolveProtocol((McpException) exceptionMapper.mapException(new IllegalArgumentException("cannot deserialize")))).isEmpty();
        assertThat(resolveProtocol((McpException) exceptionMapper.mapException(new UncheckedIOException(new IOException("connection reset"))))).isEmpty();
    }

    @Test
    public void testServerAndAuthFailuresDoNotDecideTheProtocol()
    {
        assertThat(resolveProtocol(unexpectedResponse(500))).isEmpty();
        assertThat(resolveProtocol(unexpectedResponse(503))).isEmpty();
        assertThat(resolveProtocol(unexpectedResponse(401))).isEmpty();
        assertThat(resolveProtocol(unexpectedResponse(403))).isEmpty();
        assertThat(resolveProtocol(unexpectedResponse(429))).isEmpty();
    }

    private static McpException unsupportedProtocolException(String supportedVersion)
    {
        UnsupportedProtocolVersionError error = new UnsupportedProtocolVersionError(ImmutableList.of(supportedVersion), PROTOCOL_MCP_2026_07_28.value());
        return new McpException(new ErrorResponseException(400), new JsonRpcErrorDetail(UNSUPPORTED_PROTOCOL, "Unsupported protocol version", error));
    }

    private static McpException errorResponse(int statusCode, JsonRpcErrorCode errorCode)
    {
        return new McpException(new ErrorResponseException(statusCode), new JsonRpcErrorDetail(errorCode, "error"));
    }

    private static McpException unexpectedResponse(int statusCode)
    {
        UnexpectedResponseException cause = new UnexpectedResponseException(
                "HTTP " + statusCode,
                preparePost().setUri(URI.create("http://localhost:1/mcp")).build(),
                statusCode,
                ImmutableListMultimap.of());
        return (McpException) new StandardExceptionMapper().mapException(cause);
    }
}
