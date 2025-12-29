package io.airlift.mcp;

import static io.airlift.mcp.TestMcp.Mode.MEMORY_SESSIONS;

public class TestMcpMemorySessions
        extends TestMcp
{
    public TestMcpMemorySessions()
    {
        super(MEMORY_SESSIONS);
    }
}
