package io.airlift.mcp.operations;

import com.google.common.collect.ImmutableList;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import io.airlift.json.JsonMapperProvider;
import io.airlift.mcp.model.Meta;
import io.airlift.mcp.model.Prompt;
import io.airlift.mcp.model.ResourceContents;
import io.airlift.mcp.model.Tool;
import io.airlift.mcp.model.Tool.ToolAnnotations;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Optional;

import static java.nio.charset.StandardCharsets.UTF_8;

@State(Scope.Thread)
public class BenchmarkSubscriptionSnapshot
{
    @Param({"prompts", "tools", "resource_reused", "resource_recreated", "resource_changed"})
    public String kind;

    private final JsonMapper jsonMapper = new JsonMapperProvider().get();
    private List<? extends Meta<?>> items;
    private List<? extends Meta<?>> alternateItems;
    private SubscriptionSnapshot previousSnapshot;
    private String previousHash;
    private boolean alternate;

    @Setup
    public void setup()
    {
        ImmutableList.Builder<Meta<?>> builder = ImmutableList.builder();
        switch (kind) {
            case "prompts", "tools" -> {
                for (int index = 0; index < 100; index++) {
                    if (kind.equals("prompts")) {
                        builder.add(new Prompt("prompt_" + index, Optional.of("Prompt description"), Optional.empty(), List.of(), Optional.empty(), Optional.empty()));
                    }
                    else {
                        builder.add(new Tool(
                                "tool_" + index,
                                Optional.of("Tool description"),
                                Optional.empty(),
                                jsonMapper.createObjectNode().put("type", "object"),
                                Optional.empty(),
                                new ToolAnnotations(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty())));
                    }
                }
                items = builder.build();
                alternateItems = items;
            }
            case "resource_reused", "resource_recreated", "resource_changed" -> {
                String text = "line with \"quotes\" and a newline\n".repeat(2048);
                items = List.of(new ResourceContents("resource", "test://resource", "text/plain", text));
                // Alternating equal strings models a provider returning a new payload on each poll,
                // without measuring provider allocation in the subscription benchmark.
                alternateItems = List.of(new ResourceContents("resource", "test://resource", "text/plain", kind.equals("resource_changed") ? text + "changed" : new String(text.toCharArray())));
            }
            default -> throw new IllegalArgumentException(kind);
        }
        previousSnapshot = SubscriptionSnapshot.snapshot(jsonMapper, items, null);
        previousHash = hash(items);
    }

    @Benchmark
    public boolean snapshots()
    {
        SubscriptionSnapshot next = SubscriptionSnapshot.snapshot(jsonMapper, nextItems(), previousSnapshot);
        boolean unchanged = next.equals(previousSnapshot);
        previousSnapshot = next;
        return unchanged;
    }

    // The original subscription algorithm, retained as a benchmark baseline.
    @Benchmark
    public boolean serializedHashes()
    {
        String next = hash(nextItems());
        boolean unchanged = next.equals(previousHash);
        previousHash = next;
        return unchanged;
    }

    private List<? extends Meta<?>> nextItems()
    {
        alternate = !alternate;
        return (kind.equals("resource_recreated") || kind.equals("resource_changed")) && alternate ? alternateItems : items;
    }

    @SuppressWarnings("UnstableApiUsage")
    private String hash(List<? extends Meta<?>> values)
    {
        Hasher hasher = Hashing.sha256().newHasher();
        values.forEach(value -> hasher.putString(jsonMapper.writeValueAsString(value), UTF_8));
        return hasher.hash().toString();
    }
}
