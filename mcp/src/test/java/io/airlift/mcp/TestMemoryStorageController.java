package io.airlift.mcp;

import io.airlift.mcp.storage.MemoryStorageController;
import io.airlift.mcp.storage.StorageController;

import java.time.Duration;

public class TestMemoryStorageController
        extends TestStorageController
{
    private final MemoryStorageController storageController;

    public TestMemoryStorageController()
    {
        storageController = new MemoryStorageController(Duration.ofMillis(1));
    }

    @Override
    protected StorageController storageController()
    {
        return storageController;
    }
}
