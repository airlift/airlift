package io.airlift.mcp;

import com.google.inject.Binding;
import com.google.inject.Key;
import com.google.inject.TypeLiteral;
import com.google.inject.spi.Element;
import com.google.inject.spi.Elements;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

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

    private static boolean hasBinding(List<Element> elements, Key<?> key)
    {
        return elements.stream()
                .filter(Binding.class::isInstance)
                .map(Binding.class::cast)
                .map(Binding::getKey)
                .anyMatch(key::equals);
    }
}
