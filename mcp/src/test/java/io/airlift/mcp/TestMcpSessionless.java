package io.airlift.mcp;

import static io.airlift.mcp.TestMcp.Mode.SESSIONLESS;

public class TestMcpSessionless
        extends TestMcp
{
    public TestMcpSessionless()
    {
        super(SESSIONLESS);
    }
}
