package io.airlift.mcp;

import com.google.inject.Binding;
import com.google.inject.Key;
import com.google.inject.TypeLiteral;
import com.google.inject.spi.Element;
import com.google.inject.spi.Elements;
import io.airlift.mcp.operations.legacy.sessions.StandardSessionController;
import io.airlift.mcp.operations.legacy.storage.MemoryStorageController;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class TestMcpModule
{
    @Test
    public void testMcpFilterCanBeBoundToQualifiedHttpServer()
    {
        List<Element> elements = Elements.getElements(McpModule.builder()
                .withIdentityMapper(TestingIdentity.class, binding -> binding.to(TestingIdentityMapper.class))
                .withHttpServerBinding(ForTest.class)
                .build());

        assertThat(hasBinding(elements, Key.get(new TypeLiteral<Set<Filter>>() {}))).isFalse();
        assertThat(hasBinding(elements, Key.get(new TypeLiteral<Set<Filter>>() {}, ForTest.class))).isTrue();
    }

    @Test
    public void testLegacyBindingsCanBeMadeInAnyOrder()
    {
        // the order within the lambda is not something a caller should have to think about
        McpModule.builder()
                .withIdentityMapper(TestingIdentity.class, binding -> binding.to(TestingIdentityMapper.class))
                .withLegacyBindings(legacy -> legacy
                        .withSessions(binding -> binding.to(StandardSessionController.class))
                        .withStorage(binding -> binding.to(MemoryStorageController.class)))
                .build();
    }

    @Test
    public void testSessionsRequireStorage()
    {
        assertThatThrownBy(() -> McpModule.builder()
                .withIdentityMapper(TestingIdentity.class, binding -> binding.to(TestingIdentityMapper.class))
                .withLegacyBindings(legacy -> legacy.withSessions(binding -> binding.to(StandardSessionController.class)))
                .build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Storage controller binding is required");
    }

    private static boolean hasBinding(List<Element> elements, Key<?> key)
    {
        return elements.stream()
                .filter(Binding.class::isInstance)
                .map(Binding.class::cast)
                .map(Binding::getKey)
                .anyMatch(key::equals);
    }
}
