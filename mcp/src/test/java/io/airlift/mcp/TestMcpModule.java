package io.airlift.mcp;

import com.google.inject.Binding;
import com.google.inject.Key;
import com.google.inject.TypeLiteral;
import com.google.inject.spi.Element;
import com.google.inject.spi.Elements;
import com.google.inject.spi.LinkedKeyBinding;
import io.airlift.mcp.model.Protocol;
import io.airlift.mcp.operations.Operations;
import io.airlift.mcp.operations.OperationsSelector;
import io.airlift.mcp.operations.legacy.LegacyOperations;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.google.common.collect.MoreCollectors.onlyElement;
import static io.airlift.mcp.model.Protocol.PROTOCOL_MCP_2025_06_18;
import static io.airlift.mcp.model.Protocol.PROTOCOL_MCP_2025_11_25;
import static io.airlift.mcp.model.Protocol.PROTOCOL_MCP_2026_07_28;
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
    public void testOperationsSelectorIsBoundByDefault()
    {
        assertThat(operationsTarget(Optional.empty())).isEqualTo(Key.get(OperationsSelector.class));
    }

    @ParameterizedTest
    @EnumSource(value = Protocol.class, names = {"PROTOCOL_MCP_2025_06_18", "PROTOCOL_MCP_2025_11_25"})
    public void testLegacyOperationsAreBoundForLegacyMaxProtocolLevel(Protocol maxProtocolLevel)
    {
        assertThat(operationsTarget(Optional.of(maxProtocolLevel))).isEqualTo(Key.get(LegacyOperations.class));
    }

    @Test
    public void testOperationsSelectorIsBoundForNonLegacyMaxProtocolLevel()
    {
        assertThat(operationsTarget(Optional.of(PROTOCOL_MCP_2026_07_28))).isEqualTo(Key.get(OperationsSelector.class));
    }

    @Test
    public void testMaxProtocolLevelCannotBeSetTwice()
    {
        McpModule.Builder builder = McpModule.builder().withMaxProtocolLevel(PROTOCOL_MCP_2025_11_25);

        assertThatThrownBy(() -> builder.withMaxProtocolLevel(PROTOCOL_MCP_2025_06_18))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Max protocol level is already set");
    }

    private static Key<?> operationsTarget(Optional<Protocol> maxProtocolLevel)
    {
        McpModule.Builder builder = McpModule.builder()
                .withIdentityMapper(TestingIdentity.class, binding -> binding.to(TestingIdentityMapper.class));
        maxProtocolLevel.ifPresent(builder::withMaxProtocolLevel);

        return Elements.getElements(builder.build()).stream()
                .filter(LinkedKeyBinding.class::isInstance)
                .map(LinkedKeyBinding.class::cast)
                .filter(binding -> binding.getKey().equals(Key.get(Operations.class)))
                .map(LinkedKeyBinding::getLinkedKey)
                .collect(onlyElement());
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
