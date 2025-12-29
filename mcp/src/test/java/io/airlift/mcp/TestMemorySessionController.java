package io.airlift.mcp;

import io.airlift.mcp.sessions.MemorySessionController;
import io.airlift.mcp.sessions.SessionController;
import io.airlift.mcp.sessions.SessionId;
import io.airlift.mcp.sessions.SessionValueKey;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.IntStream;

import static com.google.common.collect.ImmutableList.toImmutableList;
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
        SessionId sessionId = controller.createSession(null);

        aKeys.forEach(key -> controller.setSessionValue(sessionId, key, new TypeA(key.name())));
        bKeys.forEach(key -> controller.setSessionValue(sessionId, key, new TypeB(key.name())));

        List<TypeA> aValues = aKeys.stream().map(key -> new TypeA(key.name())).collect(toImmutableList());
        List<TypeB> bValues = bKeys.stream().map(key -> new TypeB(key.name())).collect(toImmutableList());

        assertThat(list(controller, sessionId, TypeA.class, TypeA::name)).containsExactlyInAnyOrderElementsOf(aValues);
        assertThat(list(controller, sessionId, TypeB.class, TypeB::name)).containsExactlyInAnyOrderElementsOf(bValues);
    }

    private <T> List<T> list(SessionController controller, SessionId sessionId, Class<T> type, Function<T, String> mapper)
    {
        int pageSize = 12;

        List<T> results = new ArrayList<>();
        Optional<String> lastName = Optional.empty();
        do {
            List<T> thisList = controller.listSessionValues(sessionId, type, pageSize, lastName)
                    .stream()
                    .map(Map.Entry::getValue)
                    .collect(toImmutableList());
            results.addAll(thisList);
            lastName = (thisList.size() < pageSize) ? Optional.empty() : Optional.of(mapper.apply(thisList.getLast()));
        }
        while (lastName.isPresent());
        return results;
    }
}
