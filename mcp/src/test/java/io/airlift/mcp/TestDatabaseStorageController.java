package io.airlift.mcp;

import io.airlift.mcp.storage.StorageController;
import io.airlift.mcp.testing.TestingDatabaseServer;
import io.airlift.mcp.testing.TestingDatabaseStorageController;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.TestInstance;

import java.io.IOException;

import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

@TestInstance(PER_CLASS)
public class TestDatabaseStorageController
        extends TestStorageController
{
    private final TestingDatabaseServer databaseServer;
    private final TestingDatabaseStorageController storageController;

    public TestDatabaseStorageController()
    {
        databaseServer = new TestingDatabaseServer();
        storageController = new TestingDatabaseStorageController(databaseServer, new McpConfig());
        storageController.initialize();
    }

    @AfterAll
    public void cleanupAll()
            throws IOException
    {
        storageController.close();
        databaseServer.close();
    }

    @Override
    protected StorageController storageController()
    {
        return storageController;
    }
}
