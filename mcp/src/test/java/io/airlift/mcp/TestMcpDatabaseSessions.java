package io.airlift.mcp;

import static io.airlift.mcp.TestMcp.Mode.DATABASE_SESSIONS;

public class TestMcpDatabaseSessions
        extends TestMcp
{
    public TestMcpDatabaseSessions()
    {
        super(DATABASE_SESSIONS);
    }
}
