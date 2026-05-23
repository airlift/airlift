package io.airlift.mcp.operations;

import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import io.airlift.mcp.McpIdentity;
import io.airlift.mcp.model.JsonRpcRequest;
import io.airlift.mcp.model.JsonRpcResponse;
import io.airlift.mcp.model.Protocol;
import io.airlift.mcp.operations.legacy.LegacyOperations;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.util.Optional;
import java.util.Set;

import static io.airlift.mcp.model.Constants.HEADER_PROTOCOL_VERSION;
import static io.airlift.mcp.model.Protocol.PROTOCOL_MCP_2025_11_25;
import static io.airlift.mcp.model.Protocol.PROTOCOL_MCP_2026_07_28;
import static java.util.Objects.requireNonNull;

public class OperationsSelector
        implements Operations
{
    private static final Set<String> UNSUPPORTED_OLD_PROTOCOL_VERSIONS = ImmutableSet.of("2025-03-26", "2024-11-05");
    private static final Protocol LAST_LEGACY_PROTOCOL = PROTOCOL_MCP_2025_11_25;

    private final OperationsImpl operationsImpl;
    private final LegacyOperations legacyOperations;

    @Inject
    OperationsSelector(OperationsImpl operationsImpl, LegacyOperations legacyOperations)
    {
        this.operationsImpl = requireNonNull(operationsImpl, "operationsImpl is null");
        this.legacyOperations = requireNonNull(legacyOperations, "legacyOperations is null");
    }

    @Override
    public void handleRpcRequest(HttpServletRequest request, HttpServletResponse response, McpIdentity.Authenticated<?> authenticated, JsonRpcRequest<?> rpcRequest)
    {
        select(request).handleRpcRequest(request, response, authenticated, rpcRequest);
    }

    @Override
    public void handleRpcNotification(HttpServletRequest request, HttpServletResponse response, McpIdentity.Authenticated<?> authenticated, JsonRpcRequest<?> rpcRequest)
    {
        select(request).handleRpcNotification(request, response, authenticated, rpcRequest);
    }

    @Override
    public void handleRpcResponse(HttpServletRequest request, HttpServletResponse response, McpIdentity.Authenticated<?> authenticated, JsonRpcResponse<?> rpcResponse)
    {
        select(request).handleRpcResponse(request, response, authenticated, rpcResponse);
    }

    @Override
    public void handleRcpDeleteRequest(HttpServletRequest request, HttpServletResponse response, McpIdentity.Authenticated<?> authenticated)
    {
        select(request).handleRcpDeleteRequest(request, response, authenticated);
    }

    @Override
    public void handleRpcGetRequest(HttpServletRequest request, HttpServletResponse response, McpIdentity.Authenticated<?> authenticated)
    {
        select(request).handleRpcGetRequest(request, response, authenticated);
    }

    private Operations select(HttpServletRequest request)
    {
        Optional<String> maybeProtocolVersion = Optional.ofNullable(request.getHeader(HEADER_PROTOCOL_VERSION));
        Protocol protocol = maybeProtocolVersion
                // there are older versions than Airlift supports. For these, always use legacyOperations
                .map(protocolVersion -> UNSUPPORTED_OLD_PROTOCOL_VERSIONS.contains(protocolVersion) ? LAST_LEGACY_PROTOCOL.value() : protocolVersion)
                .flatMap(Protocol::of)
                // if a protocol header is present, assume the 2026 protocol
                .orElse(maybeProtocolVersion.isPresent() ? PROTOCOL_MCP_2026_07_28 : LAST_LEGACY_PROTOCOL);
        return switch (protocol) {
            case PROTOCOL_MCP_2025_11_25, PROTOCOL_MCP_2025_06_18 -> legacyOperations;
            default -> operationsImpl;
        };
    }
}
