package io.airlift.mcp;

import io.airlift.mcp.model.McpIdentity;
import io.airlift.mcp.sessions.MemorySessionController;
import io.airlift.mcp.sessions.SessionController;
import io.airlift.mcp.sessions.SessionId;
import io.airlift.mcp.sessions.SessionValueKey;

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

        SessionController controller = new MemorySessionController(Duration.ofMillis(1));
        SessionId sessionId = controller.createSession(new McpIdentity.Authenticated<>("dummy"), Optional.of(shortDuration));
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
        return new MemorySessionController();
    }
}
