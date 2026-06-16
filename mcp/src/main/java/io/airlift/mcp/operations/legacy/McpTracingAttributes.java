package io.airlift.mcp.operations.legacy;

import io.opentelemetry.api.common.AttributeKey;

import static io.opentelemetry.api.common.AttributeKey.stringKey;

// The MCP attributes were deprecated in io.opentelemetry.semconv.incubating.McpIncubatingAttributes and
// moved to the OpenTelemetry GenAI semantic conventions (https://github.com/open-telemetry/semantic-conventions-genai),
// which does not yet publish Java bindings. The keys below match that spec verbatim; replace this class with the
// generated constants once a Maven artifact for the GenAI MCP attributes is available.
final class McpTracingAttributes
{
    static final AttributeKey<String> MCP_METHOD_NAME = stringKey("mcp.method.name");
    static final AttributeKey<String> MCP_PROTOCOL_VERSION = stringKey("mcp.protocol.version");
    static final AttributeKey<String> MCP_RESOURCE_URI = stringKey("mcp.resource.uri");

    private McpTracingAttributes() {}
}
