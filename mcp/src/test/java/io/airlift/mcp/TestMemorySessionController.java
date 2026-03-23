package io.airlift.mcp;

import io.airlift.json.JsonMapperProvider;
import io.airlift.mcp.McpIdentity.Authenticated;
import io.airlift.mcp.sessions.SessionController;
import io.airlift.mcp.sessions.SessionId;
import io.airlift.mcp.sessions.SessionValueKey;
import io.airlift.mcp.sessions.StandardSessionController;
import io.airlift.mcp.storage.MemoryStorageController;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

public class TestMemorySessionController
        extends TestSessionController
{
    public void testCleanup()
            throws InterruptedException
    {
        // asserting that the following test code will execute faster than 100ms
        Duration shortDuration = Duration.ofMillis(100);

        SessionController controller = new StandardSessionController(new McpConfig(), new MemoryStorageController(Duration.ofMillis(1)), new JsonMapperProvider().get());
        SessionId sessionId = controller.createSession(new Authenticated<>("dummy"), Optional.of(shortDuration));
        for (int i = 0; i < 10; i++) {
            // keep the session alive
            boolean success = controller.setSessionValue(sessionId, SessionValueKey.of("key", String.class), "value");
            assertThat(success).isTrue();

            Thread.sleep(shortDuration.toMillis() / 2);
        }

        Thread.sleep(shortDuration.toMillis() * 2);
        boolean success = controller.setSessionValue(sessionId, SessionValueKey.of("key", String.class), "value");
        assertThat(success).isFalse();
    }

    @Override
    protected SessionController sessionController()
    {
        return new StandardSessionController(new McpConfig(), new MemoryStorageController(), new JsonMapperProvider().get());
    }
}
