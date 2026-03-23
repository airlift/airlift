package io.airlift.mcp;

import static io.airlift.mcp.TestMcp.Mode.STANDARD_DATABASE_SESSIONS;

public class TestMcpStandardDatabaseSessions
        extends TestMcp
{
    public TestMcpStandardDatabaseSessions()
    {
        super(STANDARD_DATABASE_SESSIONS);
    }
}
