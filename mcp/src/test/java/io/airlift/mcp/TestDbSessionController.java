package io.airlift.mcp;

import io.airlift.json.ObjectMapperProvider;
import io.airlift.mcp.sessions.SessionController;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.TestInstance;

import java.io.IOException;

import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

@TestInstance(PER_CLASS)
public class TestDbSessionController
        extends TestSessionController
{
    private final TestingDatabaseServer testingDatabaseServer;
    private final TestingDatabaseSessionController sessionController;

    public TestDbSessionController()
    {
        testingDatabaseServer = new TestingDatabaseServer();
        sessionController = new TestingDatabaseSessionController(testingDatabaseServer, new ObjectMapperProvider().get());
        sessionController.initialize();
    }

    @AfterAll
    public void cleanupAll()
            throws IOException
    {
        sessionController.close();
        testingDatabaseServer.close();
    }

    @Override
    protected SessionController sessionController()
    {
        return sessionController;
    }
}
