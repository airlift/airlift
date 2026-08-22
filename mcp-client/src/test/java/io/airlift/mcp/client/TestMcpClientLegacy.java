package io.airlift.mcp.client;

import static io.airlift.mcp.client.settings.ClientMode.LEGACY_PROTOCOL_ONLY;

public class TestMcpClientLegacy
        extends TestMcpClient
{
    public TestMcpClientLegacy()
    {
        super(LEGACY_PROTOCOL_ONLY);
    }
}
