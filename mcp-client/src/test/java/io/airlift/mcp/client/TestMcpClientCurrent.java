package io.airlift.mcp.client;

import static io.airlift.mcp.client.settings.ClientMode.LEGACY_PROTOCOL_DISABLED;

public class TestMcpClientCurrent
        extends TestMcpClient
{
    public TestMcpClientCurrent()
    {
        super(LEGACY_PROTOCOL_DISABLED);
    }
}
