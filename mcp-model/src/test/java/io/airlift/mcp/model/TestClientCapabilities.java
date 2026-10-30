package io.airlift.mcp.model;

import com.google.common.collect.ImmutableMap;
import io.airlift.mcp.model.InitializeRequest.ClientCapabilities;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static io.airlift.mcp.model.Constants.EXTENSION_TASKS;
import static org.assertj.core.api.Assertions.assertThat;

public class TestClientCapabilities
{
    private static final String OTHER_EXTENSION = "com.example/other";

    @Test
    public void testWithoutExtensionKeepsTheOthers()
    {
        ClientCapabilities capabilities = withExtensions(ImmutableMap.of(EXTENSION_TASKS, ImmutableMap.of(), OTHER_EXTENSION, ImmutableMap.of("enabled", true)));

        ClientCapabilities withoutTasks = capabilities.withoutExtension(EXTENSION_TASKS);

        assertThat(withoutTasks.hasExtension(EXTENSION_TASKS)).isFalse();
        assertThat(withoutTasks.extensions()).contains(ImmutableMap.of(OTHER_EXTENSION, ImmutableMap.of("enabled", true)));
        assertThat(withoutTasks.elicitation()).isEqualTo(capabilities.elicitation());
    }

    @Test
    public void testWithoutAnAbsentExtension()
    {
        ClientCapabilities capabilities = withExtensions(ImmutableMap.of(OTHER_EXTENSION, ImmutableMap.of()));

        assertThat(capabilities.withoutExtension(EXTENSION_TASKS)).isSameAs(capabilities);
        assertThat(ClientCapabilities.EMPTY.withoutExtension(EXTENSION_TASKS)).isSameAs(ClientCapabilities.EMPTY);
    }

    private static ClientCapabilities withExtensions(ImmutableMap<String, Object> extensions)
    {
        return new ClientCapabilities(Optional.empty(), Optional.empty(), Optional.of(new InitializeRequest.Elicitation()), Optional.of(extensions), Optional.empty());
    }
}
