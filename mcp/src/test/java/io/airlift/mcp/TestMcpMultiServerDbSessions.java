package io.airlift.mcp;

import io.airlift.mcp.tasks.session.SessionTask;
import org.junit.jupiter.api.AfterAll;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.concurrent.TimeUnit;

import static com.google.inject.Scopes.SINGLETON;
import static java.util.Objects.requireNonNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestMcpMultiServerDbSessions
        extends MultiServerSessionsTestBase
{
    private static class DbTestingContext
            extends TestingContext
    {
        private final TestingDatabaseServer databaseServer;

        private DbTestingContext(TestingDatabaseServer databaseServer)
        {
            super(closer -> {
                TestingDbSessionController.createSchema(databaseServer);
                closer.register(databaseServer);

                return binder -> binder.bind(TestingDatabaseServer.class).toInstance(databaseServer);
            }, binding -> binding.to(TestingDbSessionController.class).in(SINGLETON));

            this.databaseServer = requireNonNull(databaseServer, "databaseServer is null");
        }
    }

    public TestMcpMultiServerDbSessions()
    {
        super(new DbTestingContext(new TestingDatabaseServer()));
    }

    @AfterAll
    public void dbShutdown()
    {
        for (int i = 0; i < 3; ++i) {
            if (checkTasksDeleted()) {
                break;
            }
            try {
                TimeUnit.SECONDS.sleep(2);
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        assertTrue(checkTasksDeleted(), "Session tasks were not deleted from the database");
    }

    private boolean checkTasksDeleted()
    {
        return ((DbTestingContext) testingContext).databaseServer.withConnection(connection -> {
            PreparedStatement preparedStatement = connection.prepareStatement("""
                    SELECT COUNT(*)
                    FROM session_values
                    WHERE session_key_type = ?
                    """);
            preparedStatement.setString(1, SessionTask.class.getName());

            ResultSet resultSet = preparedStatement.executeQuery();
            resultSet.next();
            int count = resultSet.getInt(1);
            return count == 0;
        });
    }
}
