package io.airlift.mcp;

import io.airlift.mcp.McpIdentity.Authenticated;
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

public abstract class TestSessionController
{
    public record TypeA(String name) {}

    public record TypeB(String name) {}

    @Test
    public void testListSessionValues()
    {
        int qty = 1000;
        List<SessionValueKey<TypeA>> aKeys = IntStream.range(0, qty)
                .mapToObj(i -> SessionValueKey.of("TypeA-" + i, TypeA.class))
                .collect(toImmutableList());
        List<SessionValueKey<TypeB>> bKeys = IntStream.range(0, qty)
                .mapToObj(i -> SessionValueKey.of("TypeB-" + i, TypeB.class))
                .collect(toImmutableList());

        SessionController controller = sessionController();
        SessionId sessionId = controller.createSession(new Authenticated<>("dummy"), Optional.empty());

        aKeys.forEach(key -> controller.setSessionValue(sessionId, key, new TypeA(key.name())));
        bKeys.forEach(key -> controller.setSessionValue(sessionId, key, new TypeB(key.name())));

        List<TypeA> aValues = aKeys.stream().map(key -> new TypeA(key.name())).collect(toImmutableList());
        List<TypeB> bValues = bKeys.stream().map(key -> new TypeB(key.name())).collect(toImmutableList());

        assertThat(list(controller, sessionId, TypeA.class)).containsExactlyInAnyOrderElementsOf(aValues);
        assertThat(list(controller, sessionId, TypeB.class)).containsExactlyInAnyOrderElementsOf(bValues);
    }

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

        controller.blockUntilCondition(sessionId, key, Duration.ofSeconds(1), Optional::isPresent);

        CountDownLatch latch1 = new CountDownLatch(1);
        Future<Void> future = newVirtualThreadPerTaskExecutor().submit(() -> {
            latch1.countDown();
            controller.blockUntilCondition(sessionId, key, Duration.ofSeconds(1), Optional::isPresent);
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
            controller.blockUntilCondition(sessionId, key, Duration.ofSeconds(1), value -> value.isPresent() && value.get().equals("newValue"));
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
            controller.blockUntilCondition(sessionId, key, Duration.ofSeconds(1), value -> value.isPresent() && value.get().equals("computed"));
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
            controller.blockUntilCondition(sessionId, key, Duration.ofSeconds(1), Optional::isEmpty);
            return null;
        });

        assertThat(latch1.await(5, TimeUnit.SECONDS)).isTrue();
        TimeUnit.MILLISECONDS.sleep(50); // ensure the wait is in progress
        controller.deleteSessionValue(sessionId, key);

        assertThat(future)
                .succeedsWithin(5, TimeUnit.SECONDS);
    }

    protected abstract SessionController sessionController();

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
