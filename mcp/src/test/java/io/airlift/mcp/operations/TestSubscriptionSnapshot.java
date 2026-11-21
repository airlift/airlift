package io.airlift.mcp.operations;

import io.airlift.json.JsonMapperProvider;
import io.airlift.mcp.model.Prompt;
import io.airlift.mcp.model.ResourceContents;
import io.airlift.mcp.model.Tool;
import io.airlift.mcp.model.Tool.ToolAnnotations;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static io.airlift.mcp.operations.SubscriptionSnapshot.snapshot;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verifyNoInteractions;

public class TestSubscriptionSnapshot
{
    private static final JsonMapper JSON_MAPPER = new JsonMapperProvider().get();

    @Test
    public void testImmutableValuesDoNotRequireJsonSerialization()
    {
        JsonMapper mapper = spy(JSON_MAPPER);
        List<Prompt> prompts = List.of(new Prompt("prompt", Optional.empty(), Optional.empty(), List.of(), Optional.empty(), Optional.empty()));
        SubscriptionSnapshot previous = snapshot(mapper, prompts, null);
        assertThat(snapshot(mapper, prompts, previous)).isEqualTo(previous);
        assertThat(snapshot(mapper, List.of(), previous)).isNotEqualTo(previous);
        verifyNoInteractions(mapper);
    }

    @Test
    public void testResourcePayloads()
    {
        JsonMapper mapper = spy(JSON_MAPPER);
        ResourceContents contents = new ResourceContents("name", "test://resource", "text/plain", "hello \"world\"\n");
        SubscriptionSnapshot previous = snapshot(mapper, List.of(contents), null);
        assertThat(snapshot(mapper, List.of(contents), previous)).isEqualTo(previous);
        // Equal newly allocated strings also compare equal, without retaining either payload strongly.
        ResourceContents copy = new ResourceContents("name", "test://resource", "text/plain", new String(contents.text().orElseThrow().toCharArray()));
        assertThat(snapshot(mapper, List.of(copy), previous)).isEqualTo(previous);
        assertThat(snapshot(mapper, List.of(new ResourceContents("name", "test://resource", "text/plain", "changed")), previous)).isNotEqualTo(previous);
        ResourceContents blob = new ResourceContents(Optional.of("name"), "test://resource", "text/plain", Optional.empty(), contents.text());
        assertThat(snapshot(mapper, List.of(blob), previous)).isNotEqualTo(previous);
        verifyNoInteractions(mapper);
    }

    @Test
    public void testResourceFingerprintBoundaries()
    {
        for (String text : List.of("", "x", "x".repeat(4096), "x".repeat(4097), "\uD800", "\uDC00", "\uD83D\uDE00")) {
            ResourceContents contents = new ResourceContents("name", "test://resource", "text/plain", text);
            SubscriptionSnapshot first = snapshot(JSON_MAPPER, List.of(contents), null);
            // Recompute the fingerprint independently, including the partial final chunk.
            assertThat(snapshot(JSON_MAPPER, List.of(contents), null)).isEqualTo(first);
            ResourceContents changed = new ResourceContents("name", "test://resource", "text/plain", text + "?");
            assertThat(snapshot(JSON_MAPPER, List.of(changed), first)).isNotEqualTo(first);
        }
        SubscriptionSnapshot surrogate = snapshot(JSON_MAPPER, List.of(new ResourceContents("name", "test://resource", "text/plain", "\uD800")), null);
        assertThat(snapshot(JSON_MAPPER, List.of(new ResourceContents("name", "test://resource", "text/plain", "?")), surrogate)).isNotEqualTo(surrogate);
    }

    @Test
    public void testInPlaceSchemaAndMetadataChanges()
    {
        ObjectNode inputSchema = JSON_MAPPER.createObjectNode().put("type", "object");
        ObjectNode outputSchema = JSON_MAPPER.createObjectNode().put("type", "string");
        List<String> metadata = new ArrayList<>(List.of("before"));
        Tool tool = new Tool(
                "tool",
                Optional.empty(),
                Optional.empty(),
                inputSchema,
                Optional.of(outputSchema),
                new ToolAnnotations(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()),
                Optional.empty(),
                Optional.of(Map.of("nested", metadata)));
        SubscriptionSnapshot previous = snapshot(JSON_MAPPER, List.of(tool), null);
        assertThat(snapshot(JSON_MAPPER, List.of(tool), previous)).isEqualTo(previous);
        inputSchema.put("additionalProperties", false);
        SubscriptionSnapshot changedInput = snapshot(JSON_MAPPER, List.of(tool), previous);
        assertThat(changedInput).isNotEqualTo(previous);
        outputSchema.put("type", "number");
        SubscriptionSnapshot changedOutput = snapshot(JSON_MAPPER, List.of(tool), changedInput);
        assertThat(changedOutput).isNotEqualTo(changedInput);
        metadata.add("after");
        assertThat(snapshot(JSON_MAPPER, List.of(tool), changedOutput)).isNotEqualTo(changedOutput);
    }
}
