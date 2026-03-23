package io.airlift.mcp;

import io.airlift.mcp.McpIdentity.Authenticated;
import io.airlift.mcp.sessions.BlockingResult;
import io.airlift.mcp.sessions.BlockingResult.TimedOut;
import io.airlift.mcp.sessions.SessionController;
import io.airlift.mcp.sessions.SessionId;
import io.airlift.mcp.sessions.SessionValueKey;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import static java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor;
import static org.assertj.core.api.Assertions.assertThat;

public abstract class TestSessionController
{
    @Test
    public void testConditions()
            throws InterruptedException
    {
        SessionController controller = sessionController();
        SessionId sessionId = controller.createSession(new Authenticated<>("dummy"), Optional.empty());

        SessionValueKey<String> key = new SessionValueKey<>("key", String.class);

        Semaphore semaphore = new Semaphore(0);

        // wait for key to appear

        newVirtualThreadPerTaskExecutor().execute(() -> {
            try {
                assertThat(semaphore.tryAcquire(5, TimeUnit.SECONDS)).isTrue();
                controller.setSessionValue(sessionId, key, "value");
            }
            catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });

        BlockingResult<String> blockingResult = controller.blockUntil(sessionId, key, Duration.ofSeconds(1), Optional::isPresent);
        assertThat(blockingResult).isInstanceOf(TimedOut.class);

        CountDownLatch latch1 = new CountDownLatch(1);
        Future<Void> future = newVirtualThreadPerTaskExecutor().submit(() -> {
            latch1.countDown();
            controller.blockUntil(sessionId, key, Duration.ofSeconds(1), Optional::isPresent);
            return null;
        });

        assertThat(latch1.await(5, TimeUnit.SECONDS)).isTrue();
        semaphore.release();

        assertThat(future)
                .succeedsWithin(5, TimeUnit.SECONDS);

        // wait for change

        CountDownLatch latch2 = new CountDownLatch(1);
        future = newVirtualThreadPerTaskExecutor().submit(() -> {
            latch2.countDown();
            controller.blockUntil(sessionId, key, Duration.ofSeconds(1), value -> value.filter("newValue"::equals).isPresent());
            return null;
        });

        assertThat(latch1.await(5, TimeUnit.SECONDS)).isTrue();
        TimeUnit.MILLISECONDS.sleep(50); // ensure the wait is in progress
        controller.setSessionValue(sessionId, key, "newValue");

        assertThat(future)
                .succeedsWithin(5, TimeUnit.SECONDS);

        // wait for computed change

        CountDownLatch latch3 = new CountDownLatch(1);
        future = newVirtualThreadPerTaskExecutor().submit(() -> {
            latch3.countDown();
            controller.blockUntil(sessionId, key, Duration.ofSeconds(1), value -> value.filter("computed"::equals).isPresent());
            return null;
        });

        assertThat(latch1.await(5, TimeUnit.SECONDS)).isTrue();
        TimeUnit.MILLISECONDS.sleep(50); // ensure the wait is in progress
        Optional<String> computed = controller.computeSessionValue(sessionId, key, _ -> Optional.of("computed"));
        assertThat(computed).contains("computed");

        assertThat(future)
                .succeedsWithin(5, TimeUnit.SECONDS);

        // wait for deletion

        CountDownLatch latch4 = new CountDownLatch(1);
        future = newVirtualThreadPerTaskExecutor().submit(() -> {
            latch4.countDown();
            controller.blockUntil(sessionId, key, Duration.ofSeconds(1), Optional::isEmpty);
            return null;
        });

        assertThat(latch1.await(5, TimeUnit.SECONDS)).isTrue();
        TimeUnit.MILLISECONDS.sleep(50); // ensure the wait is in progress
        controller.deleteSessionValue(sessionId, key);

        assertThat(future)
                .succeedsWithin(5, TimeUnit.SECONDS);
    }

    protected abstract SessionController sessionController();
}
