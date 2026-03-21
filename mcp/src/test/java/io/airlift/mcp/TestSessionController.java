package io.airlift.mcp;

import io.airlift.mcp.legacy.sessions.LegacyBlockingResult;
import io.airlift.mcp.legacy.sessions.LegacyBlockingResult.TimedOut;
import io.airlift.mcp.legacy.sessions.LegacySession;
import io.airlift.mcp.legacy.sessions.LegacySessionController;
import io.airlift.mcp.legacy.sessions.LegacySessionId;
import io.airlift.mcp.legacy.sessions.LegacySessionValueKey;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.assertj.core.api.Assertions.assertThat;

public class TestSessionController
{
    public record TypeA(String name) {}

    public record TypeB(String name) {}

    @Test
    public void testListSessionValues()
    {
        int qty = 1000;
        List<LegacySessionValueKey<TypeA>> aKeys = IntStream.range(0, qty)
                .mapToObj(i -> LegacySessionValueKey.of("TypeA-" + i, TypeA.class))
                .collect(toImmutableList());
        List<LegacySessionValueKey<TypeB>> bKeys = IntStream.range(0, qty)
                .mapToObj(i -> LegacySessionValueKey.of("TypeB-" + i, TypeB.class))
                .collect(toImmutableList());

        LegacySessionController controller = new LegacySessionController(new McpConfig());
        LegacySession session = controller.createSession();

        aKeys.forEach(key -> session.setValue(key, new TypeA(key.name())));
        bKeys.forEach(key -> session.setValue(key, new TypeB(key.name())));

        List<TypeA> aValues = aKeys.stream().map(key -> new TypeA(key.name())).collect(toImmutableList());
        List<TypeB> bValues = bKeys.stream().map(key -> new TypeB(key.name())).collect(toImmutableList());

        assertThat(list(controller, session.sessionId(), TypeA.class)).containsExactlyInAnyOrderElementsOf(aValues);
        assertThat(list(controller, session.sessionId(), TypeB.class)).containsExactlyInAnyOrderElementsOf(bValues);
    }

    @Test
    public void testConditions()
            throws InterruptedException
    {
        LegacySessionController controller = new LegacySessionController(new McpConfig());
        LegacySession session = controller.createSession();

        LegacySessionValueKey<String> key = new LegacySessionValueKey<>("key", String.class);

        Semaphore semaphore = new Semaphore(0);

        // wait for key to appear

        newVirtualThreadPerTaskExecutor().execute(() -> {
            try {
                assertThat(semaphore.tryAcquire(5, TimeUnit.SECONDS)).isTrue();
                session.setValue(key, "value");
            }
            catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });

        LegacyBlockingResult<String> blockingResult = session.blockUntil(key, Duration.ofSeconds(1), Optional::isPresent);
        assertThat(blockingResult).isInstanceOf(TimedOut.class);

        CountDownLatch latch1 = new CountDownLatch(1);
        Future<Void> future = newVirtualThreadPerTaskExecutor().submit(() -> {
            latch1.countDown();
            session.blockUntil(key, Duration.ofSeconds(1), Optional::isPresent);
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
            session.blockUntil(key, Duration.ofSeconds(1), value -> value.filter("newValue"::equals).isPresent());
            return null;
        });

        assertThat(latch1.await(5, TimeUnit.SECONDS)).isTrue();
        MILLISECONDS.sleep(50); // ensure the wait is in progress
        session.setValue(key, "newValue");

        assertThat(future)
                .succeedsWithin(5, TimeUnit.SECONDS);

        // wait for computed change

        CountDownLatch latch3 = new CountDownLatch(1);
        future = newVirtualThreadPerTaskExecutor().submit(() -> {
            latch3.countDown();
            session.blockUntil(key, Duration.ofSeconds(1), value -> value.filter("computed"::equals).isPresent());
            return null;
        });

        assertThat(latch1.await(5, TimeUnit.SECONDS)).isTrue();
        MILLISECONDS.sleep(50); // ensure the wait is in progress
        Optional<String> computed = session.computeValue(key, _ -> Optional.of("computed"));
        assertThat(computed).contains("computed");

        assertThat(future)
                .succeedsWithin(5, TimeUnit.SECONDS);

        // wait for deletion

        CountDownLatch latch4 = new CountDownLatch(1);
        future = newVirtualThreadPerTaskExecutor().submit(() -> {
            latch4.countDown();
            session.blockUntil(key, Duration.ofSeconds(1), Optional::isEmpty);
            return null;
        });

        assertThat(latch1.await(5, TimeUnit.SECONDS)).isTrue();
        MILLISECONDS.sleep(50); // ensure the wait is in progress
        session.deleteValue(key);

        assertThat(future)
                .succeedsWithin(5, TimeUnit.SECONDS);
    }

    @Test
    public void testCleanup()
            throws InterruptedException
    {
        // asserting that the following test code will execute faster than 100ms
        Duration shortDuration = Duration.ofMillis(100);

        McpConfig config = new McpConfig().setDefaultSessionTimeout(new io.airlift.units.Duration(shortDuration.toMillis(), MILLISECONDS));

        LegacySessionController controller = new LegacySessionController(config);
        LegacySession session = controller.createSession();

        for (int i = 0; i < 10; i++) {
            // keep the session alive
            controller.session(session.sessionId());

            assertThat(session.isValid()).isTrue();

            Thread.sleep(shortDuration.toMillis() / 2);
        }

        Thread.sleep(shortDuration.toMillis() * 2);
        assertThat(session.isValid()).isFalse();
    }

    private <T> List<T> list(LegacySessionController controller, LegacySessionId sessionId, Class<T> type)
    {
        Map<LegacySessionValueKey<?>, Object> sessionValues = controller.sessionValues(sessionId);
        return sessionValues.entrySet()
                .stream()
                .flatMap(entry -> {
                    LegacySessionValueKey<?> key = entry.getKey();
                    if (key.type().equals(type)) {
                        return Stream.of(entry.getValue());
                    }
                    return Stream.of();
                })
                .map(type::cast)
                .collect(toImmutableList());
    }
}
