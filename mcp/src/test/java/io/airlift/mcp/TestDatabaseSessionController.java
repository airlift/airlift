package io.airlift.mcp;

import com.fasterxml.jackson.databind.json.JsonMapper;
import io.airlift.json.JsonMapperProvider;
import io.airlift.mcp.sessions.SessionController;
import io.airlift.mcp.sessions.StandardSessionController;
import io.airlift.mcp.testing.TestingDatabaseServer;
import io.airlift.mcp.testing.TestingDatabaseStorageController;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.TestInstance;

import java.io.IOException;

import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

@TestInstance(PER_CLASS)
public class TestDatabaseSessionController
        extends TestSessionController
{
    private final TestingDatabaseServer testingDatabaseServer;
    private final JsonMapper jsonMapper;
    private final McpConfig mcpConfig;
    private final TestingDatabaseStorageController storageController;

    public TestDatabaseSessionController()
    {
        jsonMapper = new JsonMapperProvider().get();

        testingDatabaseServer = new TestingDatabaseServer();
        mcpConfig = new McpConfig();

        storageController = new TestingDatabaseStorageController(testingDatabaseServer, mcpConfig);
        storageController.initialize();
    }

    @AfterAll
    public void cleanupAll()
            throws IOException
    {
        storageController.close();
        testingDatabaseServer.close();
    }

    @Override
    protected SessionController sessionController()
    {
        return new StandardSessionController(mcpConfig, storageController, jsonMapper);
    }
}
