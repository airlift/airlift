package io.airlift.mcp;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import io.airlift.mcp.sessions.MemorySessionController;
import io.airlift.mcp.sessions.MemorySessionController.Session;
import io.airlift.mcp.sessions.SessionId;

public class TestMcpMultiServerMemorySessions
        extends MultiServerSessionsTestBase
{
    private static class MemoryTestingContext
            extends TestingContext
    {
        private MemoryTestingContext(Cache<SessionId, Session> sessions)
        {
            super(_ -> _ -> {}, binding -> binding.toInstance(new MemorySessionController(sessions)));
        }
    }

    public TestMcpMultiServerMemorySessions()
    {
        super(new MemoryTestingContext(CacheBuilder.newBuilder().build()));
    }
}
