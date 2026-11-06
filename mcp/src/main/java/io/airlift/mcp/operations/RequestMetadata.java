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

import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static io.airlift.mcp.McpException.exception;
import static io.airlift.mcp.McpException.exceptionWithData;
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
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;

public record RequestMetadata(
        Protocol protocol,
        Optional<Implementation> clientInfo,
        ClientCapabilities clientCapabilities,
        Optional<LoggingLevel> loggingLevel,
        Optional<Object> progressToken,
        Optional<String> mcpName)
{
    public static final List<String> SUPPORTED_VERSIONS = Stream.of(Protocol.values())
            .map(Protocol::value)
            .collect(toImmutableList());

    private static final String ENCODED_VALUE_PREFIX = "=?base64?";
    private static final String ENCODED_VALUE_SUFFIX = "?=";

    public RequestMetadata
    {
        requireNonNull(protocol, "protocol is null");
        requireNonNull(clientInfo, "clientInfo is null");
        requireNonNull(clientCapabilities, "clientCapabilities is null");
        requireNonNull(loggingLevel, "loggingLevel is null");
        requireNonNull(progressToken, "progressToken is null");
        requireNonNull(mcpName, "mcpName is null");
    }

    public static RequestMetadata fromRequest(JsonMapper jsonMapper, HttpServletRequest request, Meta<?> metadata, String mcpMethod, ValidationMode validationMode)
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
        Optional<String> mcpNameHeader = optional(request, HEADER_MCP_NAME).map(RequestMetadata::decodeHeaderValue);

        Optional<Implementation> clientInfo = optional(jsonMapper, metadata, METADATA_CLIENT_INFO, Implementation.class);
        ClientCapabilities clientCapabilities = required(jsonMapper, metadata, METADATA_CLIENT_CAPABILITIES, ClientCapabilities.class);
        Optional<LoggingLevel> loggingLevel = optional(jsonMapper, metadata, METADATA_CLIENT_LOG_LEVEL, LoggingLevel.class);
        Optional<Object> progressToken = optional(jsonMapper, metadata, METADATA_PROGRESS_TOKEN, Object.class);

        return new RequestMetadata(protocol, clientInfo, clientCapabilities, loggingLevel, progressToken, mcpNameHeader);
    }

    private static McpException unsupportedProtocol(String protocolVersionHeader)
    {
        // https://modelcontextprotocol.io/specification/2026-07-28/basic/lifecycle#protocol-version-negotiation
        Map<String, Object> params = ImmutableMap.of("supported", SUPPORTED_VERSIONS, "requested", protocolVersionHeader);
        return exceptionWithData(UNSUPPORTED_PROTOCOL, "Unsupported protocol version", params);
    }

    // https://modelcontextprotocol.io/specification/2026-07-28/basic/transports/streamable-http#value-encoding
    // a value that cannot be sent as a plain header value arrives base64 encoded, and must be decoded before it is
    // compared to the request body
    private static String decodeHeaderValue(String value)
    {
        String stripped = value.strip();
        boolean isEncoded = stripped.startsWith(ENCODED_VALUE_PREFIX)
                && stripped.endsWith(ENCODED_VALUE_SUFFIX)
                && (stripped.length() >= ENCODED_VALUE_PREFIX.length() + ENCODED_VALUE_SUFFIX.length());
        if (!isEncoded) {
            return stripped;
        }

        String encoded = stripped.substring(ENCODED_VALUE_PREFIX.length(), stripped.length() - ENCODED_VALUE_SUFFIX.length());
        try {
            return new String(Base64.getDecoder().decode(encoded), UTF_8);
        }
        catch (IllegalArgumentException _) {
            throw exception(HEADER_MISMATCH, "%s is not valid base64: %s".formatted(HEADER_MCP_NAME, value));
        }
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

    private static <T> T required(JsonMapper jsonMapper, Meta<?> metadata, String name, Class<T> clazz)
    {
        return optional(jsonMapper, metadata, name, clazz)
                .orElseThrow(() -> exception(INVALID_PARAMS, "Missing required metadata: " + name));
    }

    private static <T> Optional<T> optional(JsonMapper jsonMapper, Meta<?> metadata, String name, Class<T> clazz)
    {
        return metadata.meta()
                .flatMap(values -> {
                    if (!values.containsKey(name)) {
                        return Optional.empty();
                    }

                    Object value = values.get(name);
                    if (value == null) {
                        throw exception(INVALID_PARAMS, "Metadata value is not the correct type: null");
                    }

                    try {
                        if (clazz.equals(Object.class)) {
                            return Optional.of(clazz.cast(value));
                        }

                        return Optional.of(jsonMapper.convertValue(value, clazz));
                    }
                    catch (Exception e) {
                        throw exception(INVALID_PARAMS, "Metadata value is not the correct type: " + value.getClass().getSimpleName());
                    }
                });
    }
}
