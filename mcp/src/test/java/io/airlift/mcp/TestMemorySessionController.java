package io.airlift.mcp;

import io.airlift.mcp.sessions.MemorySessionController;
import io.airlift.mcp.sessions.SessionController;
import io.airlift.mcp.sessions.SessionId;
import io.airlift.mcp.sessions.SessionValueKey;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor;
import static org.assertj.core.api.Assertions.assertThat;

public class TestMemorySessionController
{
    @Test
    public void testListSessionValues()
    {
        record TypeA(String name) {}

        record TypeB(String name) {}

        int qty = 1000;
        List<SessionValueKey<TypeA>> aKeys = IntStream.range(0, qty)
                .mapToObj(i -> SessionValueKey.of("TypeA-" + i, TypeA.class))
                .collect(toImmutableList());
        List<SessionValueKey<TypeB>> bKeys = IntStream.range(0, qty)
                .mapToObj(i -> SessionValueKey.of("TypeB-" + i, TypeB.class))
                .collect(toImmutableList());

        SessionController controller = new MemorySessionController();
        SessionId sessionId = controller.createSession(Optional.empty(), Optional.empty());

        aKeys.forEach(key -> controller.setSessionValue(sessionId, key, new TypeA(key.name())));
        bKeys.forEach(key -> controller.setSessionValue(sessionId, key, new TypeB(key.name())));

        List<TypeA> aValues = aKeys.stream().map(key -> new TypeA(key.name())).collect(toImmutableList());
        List<TypeB> bValues = bKeys.stream().map(key -> new TypeB(key.name())).collect(toImmutableList());

        assertThat(list(controller, sessionId, TypeA.class)).containsExactlyInAnyOrderElementsOf(aValues);
        assertThat(list(controller, sessionId, TypeB.class)).containsExactlyInAnyOrderElementsOf(bValues);
    }

    @Test
    public void testCleanup()
            throws InterruptedException
    {
        // asserting that the following test code will execute faster than 100ms
        Duration shortDuration = Duration.ofMillis(100);

        SessionController controller = new MemorySessionController(Duration.ofMillis(1));
        SessionId sessionId = controller.createSession(Optional.empty(), Optional.of(shortDuration));
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

    @Test
    public void testConditions()
            throws InterruptedException
    {
        SessionController controller = new MemorySessionController();
        SessionId sessionId = controller.createSession(Optional.empty(), Optional.empty());

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

        boolean result = controller.blockUntilCondition(sessionId, key, Duration.ofSeconds(1), Optional::isPresent).orElseThrow();
        assertThat(result).isFalse();

        CountDownLatch latch1 = new CountDownLatch(1);
        Future<Boolean> future = newVirtualThreadPerTaskExecutor().submit(() -> {
            latch1.countDown();
            return controller.blockUntilCondition(sessionId, key, Duration.ofSeconds(1), Optional::isPresent).orElseThrow();
        });

        assertThat(latch1.await(5, TimeUnit.SECONDS)).isTrue();
        semaphore.release();

        assertThat(future)
                .succeedsWithin(5, TimeUnit.SECONDS)
                .isEqualTo(true);

        // wait for change

        CountDownLatch latch2 = new CountDownLatch(1);
        future = newVirtualThreadPerTaskExecutor().submit(() -> {
            latch2.countDown();
            return controller.blockUntilCondition(sessionId, key, Duration.ofSeconds(1), value -> value.isPresent() && value.get().equals("newValue")).orElseThrow();
        });

        assertThat(latch1.await(5, TimeUnit.SECONDS)).isTrue();
        TimeUnit.MILLISECONDS.sleep(50); // ensure the wait is in progress
        controller.setSessionValue(sessionId, key, "newValue");

        assertThat(future)
                .succeedsWithin(5, TimeUnit.SECONDS)
                .isEqualTo(true);

        // wait for computed change

        CountDownLatch latch3 = new CountDownLatch(1);
        future = newVirtualThreadPerTaskExecutor().submit(() -> {
            latch3.countDown();
            return controller.blockUntilCondition(sessionId, key, Duration.ofSeconds(1), value -> value.isPresent() && value.get().equals("computed")).orElseThrow();
        });

        assertThat(latch1.await(5, TimeUnit.SECONDS)).isTrue();
        TimeUnit.MILLISECONDS.sleep(50); // ensure the wait is in progress
        controller.computeSessionValue(sessionId, key, _ -> Optional.of("computed"));

        assertThat(future)
                .succeedsWithin(5, TimeUnit.SECONDS)
                .isEqualTo(true);

        // wait for deletion

        CountDownLatch latch4 = new CountDownLatch(1);
        future = newVirtualThreadPerTaskExecutor().submit(() -> {
            latch4.countDown();
            return controller.blockUntilCondition(sessionId, key, Duration.ofSeconds(1), Optional::isEmpty).orElseThrow();
        });

        assertThat(latch1.await(5, TimeUnit.SECONDS)).isTrue();
        TimeUnit.MILLISECONDS.sleep(50); // ensure the wait is in progress
        controller.deleteSessionValue(sessionId, key);

        assertThat(future)
                .succeedsWithin(5, TimeUnit.SECONDS)
                .isEqualTo(true);
    }

    private <T> List<T> list(SessionController controller, SessionId sessionId, Class<T> type)
    {
        int pageSize = 12;

        List<T> results = new ArrayList<>();
        Optional<String> lastName = Optional.empty();
        do {
            List<Map.Entry<String, T>> thisList = controller.listSessionValues(sessionId, type, pageSize, lastName);
            thisList.forEach(entry -> results.add(entry.getValue()));
            lastName = (thisList.size() < pageSize) ? Optional.empty() : Optional.of(thisList.getLast().getKey());
        }
        while (lastName.isPresent());
        return results;
    }
}
