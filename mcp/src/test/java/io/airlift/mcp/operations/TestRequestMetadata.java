package io.airlift.mcp.operations;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.google.common.collect.ImmutableMap;
import io.airlift.json.JsonMapperProvider;
import io.airlift.mcp.McpException;
import io.airlift.mcp.model.Implementation;
import io.airlift.mcp.model.InitializeRequest.ClientCapabilities;
import io.airlift.mcp.model.Meta;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static io.airlift.mcp.model.Constants.HEADER_MCP_METHOD;
import static io.airlift.mcp.model.Constants.HEADER_MCP_NAME;
import static io.airlift.mcp.model.Constants.HEADER_PROTOCOL_VERSION;
import static io.airlift.mcp.model.Constants.METADATA_CLIENT_CAPABILITIES;
import static io.airlift.mcp.model.Constants.METADATA_CLIENT_INFO;
import static io.airlift.mcp.model.Constants.METADATA_PROTOCOL_VERSION;
import static io.airlift.mcp.model.JsonRpcErrorCode.INVALID_PARAMS;
import static io.airlift.mcp.model.Protocol.LATEST_PROTOCOL;
import static io.airlift.mcp.operations.ValidationMode.STRICT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

public class TestRequestMetadata
{
    private static final String MCP_METHOD = "tools/list";
    private static final JsonMapper JSON_MAPPER = new JsonMapperProvider().get();

    @Test
    public void testClientInfoPresent()
    {
        Meta<?> metadata = metadata(ImmutableMap.<String, Object>builder()
                .putAll(requiredMetadata())
                .put(METADATA_CLIENT_INFO, ImmutableMap.of("name", "test client", "version", "1"))
                .buildOrThrow());
        HttpServletRequest request = request();

        RequestMetadata requestMetadata = RequestMetadata.fromRequest(JSON_MAPPER, request, metadata, MCP_METHOD, STRICT);

        assertThat(requestMetadata.clientInfo()).contains(new Implementation("test client", "1"));
        assertThat(requestMetadata.clientCapabilities()).isEqualTo(ClientCapabilities.EMPTY);
        verifyRequestInteractions(request);
    }

    @Test
    public void testClientInfoAbsent()
    {
        HttpServletRequest request = request();

        RequestMetadata requestMetadata = RequestMetadata.fromRequest(JSON_MAPPER, request, metadata(requiredMetadata()), MCP_METHOD, STRICT);

        assertThat(requestMetadata.clientInfo()).isEmpty();
        assertThat(requestMetadata.clientCapabilities()).isEqualTo(ClientCapabilities.EMPTY);
        verifyRequestInteractions(request);
    }

    @Test
    public void testClientCapabilitiesRemainRequired()
    {
        Meta<?> metadata = metadata(ImmutableMap.of(METADATA_PROTOCOL_VERSION, LATEST_PROTOCOL.value()));
        HttpServletRequest request = request();

        assertThatThrownBy(() -> RequestMetadata.fromRequest(JSON_MAPPER, request, metadata, MCP_METHOD, STRICT))
                .isInstanceOfSatisfying(McpException.class, exception -> {
                    assertThat(exception.errorDetail().code()).isEqualTo(INVALID_PARAMS.code());
                    assertThat(exception.errorDetail().message()).isEqualTo("Missing required metadata: " + METADATA_CLIENT_CAPABILITIES);
                });
        verifyRequestInteractions(request);
    }

    @Test
    public void testClientInfoValidatedWhenPresent()
    {
        Meta<?> metadata = metadata(ImmutableMap.<String, Object>builder()
                .putAll(requiredMetadata())
                .put(METADATA_CLIENT_INFO, "invalid")
                .buildOrThrow());
        HttpServletRequest request = request();

        assertThatThrownBy(() -> RequestMetadata.fromRequest(JSON_MAPPER, request, metadata, MCP_METHOD, STRICT))
                .isInstanceOfSatisfying(McpException.class, exception -> {
                    assertThat(exception.errorDetail().code()).isEqualTo(INVALID_PARAMS.code());
                    assertThat(exception.errorDetail().message()).isEqualTo("Metadata value is not the correct type: String");
                });
        verifyRequestInteractions(request);
    }

    @Test
    public void testClientInfoNullRejected()
    {
        Map<String, Object> values = new HashMap<>(requiredMetadata());
        values.put(METADATA_CLIENT_INFO, null);
        HttpServletRequest request = request();

        assertThatThrownBy(() -> RequestMetadata.fromRequest(JSON_MAPPER, request, metadata(values), MCP_METHOD, STRICT))
                .isInstanceOfSatisfying(McpException.class, exception -> {
                    assertThat(exception.errorDetail().code()).isEqualTo(INVALID_PARAMS.code());
                    assertThat(exception.errorDetail().message()).isEqualTo("Metadata value is not the correct type: null");
                });
        verifyRequestInteractions(request);
    }

    private static Map<String, Object> requiredMetadata()
    {
        return ImmutableMap.of(
                METADATA_PROTOCOL_VERSION, LATEST_PROTOCOL.value(),
                METADATA_CLIENT_CAPABILITIES, ImmutableMap.of());
    }

    private static Meta<?> metadata(Map<String, Object> values)
    {
        return new TestMeta(Optional.of(values));
    }

    private static HttpServletRequest request()
    {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader(HEADER_PROTOCOL_VERSION)).thenReturn(LATEST_PROTOCOL.value());
        when(request.getHeader(HEADER_MCP_METHOD)).thenReturn(MCP_METHOD);
        return request;
    }

    private static void verifyRequestInteractions(HttpServletRequest request)
    {
        verify(request).getHeader(HEADER_PROTOCOL_VERSION);
        verify(request).getHeader(HEADER_MCP_METHOD);
        verify(request).getHeader(HEADER_MCP_NAME);
        verifyNoMoreInteractions(request);
    }

    private record TestMeta(Optional<Map<String, Object>> meta)
            implements Meta<TestMeta>
    {
        @Override
        public TestMeta withMeta(Map<String, Object> meta)
        {
            return new TestMeta(Optional.of(meta));
        }
    }
}
