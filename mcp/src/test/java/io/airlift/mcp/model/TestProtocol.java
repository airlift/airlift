package io.airlift.mcp.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static io.airlift.mcp.model.Protocol.LAST_LEGACY_PROTOCOL;
import static io.airlift.mcp.model.Protocol.LATEST_PROTOCOL;
import static io.airlift.mcp.model.Protocol.PROTOCOL_MCP_2025_06_18;
import static io.airlift.mcp.model.Protocol.PROTOCOL_MCP_2025_11_25;
import static io.airlift.mcp.model.Protocol.PROTOCOL_MCP_2026_07_28;
import static java.util.Arrays.stream;
import static org.assertj.core.api.Assertions.assertThat;

public class TestProtocol
{
    @Test
    public void testLevelsIncreaseWithDeclarationOrder()
    {
        List<Integer> levels = stream(Protocol.values())
                .map(Protocol::level)
                .collect(toImmutableList());

        assertThat(levels).isSorted();
        assertThat(levels).doesNotHaveDuplicates();
    }

    @Test
    public void testLegacyProtocolsPrecedeTheLatest()
    {
        assertThat(LAST_LEGACY_PROTOCOL.isAtMost(LATEST_PROTOCOL)).isTrue();
        assertThat(LATEST_PROTOCOL.isAtMost(LAST_LEGACY_PROTOCOL)).isFalse();
    }

    @Test
    public void testIsAtMost()
    {
        assertThat(PROTOCOL_MCP_2025_06_18.isAtMost(PROTOCOL_MCP_2025_11_25)).isTrue();
        assertThat(PROTOCOL_MCP_2025_11_25.isAtMost(PROTOCOL_MCP_2025_11_25)).isTrue();
        assertThat(PROTOCOL_MCP_2026_07_28.isAtMost(PROTOCOL_MCP_2025_11_25)).isFalse();
    }

    @Test
    public void testMin()
    {
        assertThat(Protocol.min(PROTOCOL_MCP_2026_07_28, PROTOCOL_MCP_2025_06_18)).isEqualTo(PROTOCOL_MCP_2025_06_18);
        assertThat(Protocol.min(PROTOCOL_MCP_2025_06_18, PROTOCOL_MCP_2026_07_28)).isEqualTo(PROTOCOL_MCP_2025_06_18);
        assertThat(Protocol.min(PROTOCOL_MCP_2025_11_25, PROTOCOL_MCP_2025_11_25)).isEqualTo(PROTOCOL_MCP_2025_11_25);
    }
}
