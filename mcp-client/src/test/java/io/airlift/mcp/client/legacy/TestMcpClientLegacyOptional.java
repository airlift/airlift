package io.airlift.mcp.client.legacy;

import io.airlift.mcp.client.TestMcpClient;

import static io.airlift.mcp.client.settings.ClientMode.LEGACY_PROTOCOL_OPTIONAL;

/**
 * The testing server speaks the current protocol, so this mode never actually falls back - which is the point: it
 * runs the whole suite through {@code LegacyOptionalConnection} on the path where the first request succeeds,
 * which previously had no coverage at all.
 */
public class TestMcpClientLegacyOptional
        extends TestMcpClient
{
    public TestMcpClientLegacyOptional()
    {
        super(LEGACY_PROTOCOL_OPTIONAL);
    }
}
