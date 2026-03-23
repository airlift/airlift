package io.airlift.mcp;

import static io.airlift.mcp.TestMcp.Mode.STANDARD_SESSIONS;

public class TestMcpStandardSessions
        extends TestMcp
{
    public TestMcpStandardSessions()
    {
        super(STANDARD_SESSIONS);
    }
}
