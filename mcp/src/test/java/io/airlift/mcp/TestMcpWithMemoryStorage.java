package io.airlift.mcp;

import io.airlift.mcp.storage.MemoryStorage;

import java.util.Optional;

public class TestMcpWithMemoryStorage
        extends TestMcp
{
    public TestMcpWithMemoryStorage()
    {
        super(MemoryStorage.class, Optional.empty());
    }
}
