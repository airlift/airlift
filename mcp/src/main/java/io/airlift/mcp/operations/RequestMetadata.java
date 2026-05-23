package io.airlift.mcp.operations;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.google.common.collect.ImmutableMap;
import io.airlift.mcp.McpException;
import io.airlift.mcp.model.Implementation;
import io.airlift.mcp.model.InitializeRequest.ClientCapabilities;
import io.airlift.mcp.model.JsonRpcErrorCode;
import io.airlift.mcp.model.LoggingLevel;
import io.airlift.mcp.model.Meta;
import io.airlift.mcp.model.Protocol;
import jakarta.servlet.http.HttpServletRequest;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static io.airlift.mcp.McpException.exception;
import static io.airlift.mcp.model.Constants.HEADER_MCP_METHOD;
import static io.airlift.mcp.model.Constants.HEADER_MCP_NAME;
import static io.airlift.mcp.model.Constants.HEADER_PROTOCOL_VERSION;
import static io.airlift.mcp.model.Constants.METADATA_CLIENT_CAPABILITIES;
import static io.airlift.mcp.model.Constants.METADATA_CLIENT_INFO;
import static io.airlift.mcp.model.Constants.METADATA_CLIENT_LOG_LEVEL;
import static io.airlift.mcp.model.Constants.METADATA_PROGRESS_TOKEN;
import static io.airlift.mcp.model.Constants.METADATA_PROTOCOL_VERSION;
import static io.airlift.mcp.model.JsonRpcErrorCode.HEADER_MISMATCH;
import static io.airlift.mcp.model.JsonRpcErrorCode.INVALID_PARAMS;
import static io.airlift.mcp.model.JsonRpcErrorCode.UNSUPPORTED_PROTOCOL;
import static java.util.Objects.requireNonNull;

public record RequestMetadata(
        Protocol protocol,
        Implementation implementation,
        ClientCapabilities clientCapabilities,
        Optional<LoggingLevel> loggingLevel,
        Optional<Object> progressToken,
        Optional<String> mcpName)
{
    public static final List<String> SUPPORTED_VERSIONS = Stream.of(Protocol.values())
            .map(Protocol::value)
            .collect(toImmutableList());

    public RequestMetadata
    {
        requireNonNull(protocol, "protocol is null");
        requireNonNull(implementation, "implementation is null");
        requireNonNull(clientCapabilities, "clientCapabilities is null");
        requireNonNull(loggingLevel, "loggingLevel is null");
        requireNonNull(progressToken, "progressToken is null");
        requireNonNull(mcpName, "mcpName is null");
    }

    public static RequestMetadata fromRequest(JsonMapper jsonMapper, HttpServletRequest request, Meta metadata, String mcpMethod, ValidationMode validationMode)
    {
        boolean isStrict = validationMode == ValidationMode.STRICT;

        String protocolVersionHeader = required(request, HEADER_PROTOCOL_VERSION, INVALID_PARAMS);

        Protocol protocol = Protocol.of(protocolVersionHeader)
                .orElseThrow(() -> unsupportedProtocol(protocolVersionHeader));

        if (isStrict) {
            String protocolVersionMetadata = required(jsonMapper, metadata, METADATA_PROTOCOL_VERSION, String.class);
            if (!protocolVersionMetadata.equals(protocolVersionHeader)) {
                throw exception(HEADER_MISMATCH, "Header protocol version does not match metadata protocol version. Header: %s, Metadata: %s".formatted(protocolVersionHeader, protocolVersionMetadata));
            }

            String mcpMethodHeader = required(request, HEADER_MCP_METHOD, HEADER_MISMATCH);
            if (!mcpMethodHeader.strip().equals(mcpMethod)) {
                throw exception(HEADER_MISMATCH, "%s does not match request method: %s".formatted(HEADER_MCP_METHOD, mcpMethodHeader));
            }
        }
        Optional<String> mcpNameHeader = optional(request, HEADER_MCP_NAME);

        Implementation implementation = required(jsonMapper, metadata, METADATA_CLIENT_INFO, Implementation.class);
        ClientCapabilities clientCapabilities = required(jsonMapper, metadata, METADATA_CLIENT_CAPABILITIES, ClientCapabilities.class);
        Optional<LoggingLevel> loggingLevel = optional(jsonMapper, metadata, METADATA_CLIENT_LOG_LEVEL, LoggingLevel.class);
        Optional<Object> progressToken = optional(jsonMapper, metadata, METADATA_PROGRESS_TOKEN, Object.class);

        return new RequestMetadata(protocol, implementation, clientCapabilities, loggingLevel, progressToken, mcpNameHeader);
    }

    private static McpException unsupportedProtocol(String protocolVersionHeader)
    {
        // https://modelcontextprotocol.io/specification/draft/basic/lifecycle#protocol-version-negotiation
        Map<String, Object> params = ImmutableMap.of("supported", SUPPORTED_VERSIONS, "requested", protocolVersionHeader);
        return exception(UNSUPPORTED_PROTOCOL, "Unsupported protocol version", params);
    }

    @SuppressWarnings("SameParameterValue")
    private static String required(HttpServletRequest request, String name, JsonRpcErrorCode errorCode)
    {
        return optional(request, name)
                .orElseThrow(() -> exception(errorCode, "Missing required header: " + name));
    }

    private static Optional<String> optional(HttpServletRequest request, String name)
    {
        return Optional.ofNullable(request.getHeader(name));
    }

    private static <T> T required(JsonMapper jsonMapper, Meta metadata, String name, Class<T> clazz)
    {
        return optional(jsonMapper, metadata, name, clazz)
                .orElseThrow(() -> exception(INVALID_PARAMS, "Missing required metadata: " + name));
    }

    private static <T> Optional<T> optional(JsonMapper jsonMapper, Meta metadata, String name, Class<T> clazz)
    {
        return metadata.meta()
                .flatMap(m -> Optional.ofNullable(m.get(name)))
                .map(value -> {
                    try {
                        if (clazz.equals(Object.class)) {
                            return clazz.cast(value);
                        }

                        return jsonMapper.convertValue(value, clazz);
                    }
                    catch (Exception e) {
                        throw exception(INVALID_PARAMS, "Metadata value is not the correct type: " + value.getClass().getSimpleName());
                    }
                });
    }
}
