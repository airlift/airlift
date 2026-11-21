package io.airlift.mcp.operations;

import com.google.common.collect.ImmutableList;
import com.google.common.hash.HashCode;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import io.airlift.mcp.model.Icon;
import io.airlift.mcp.model.Meta;
import io.airlift.mcp.model.Prompt;
import io.airlift.mcp.model.Resource;
import io.airlift.mcp.model.ResourceContents;
import io.airlift.mcp.model.ResourceTemplate;
import io.airlift.mcp.model.Tool;
import io.airlift.mcp.model.Tool.ToolAnnotations;
import tools.jackson.databind.json.JsonMapper;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Captures the state visible to a subscription without serializing immutable resource payloads
 * or hashing entire catalogs. Schemas and metadata can contain mutable objects, so capture their
 * JSON on every poll rather than retaining references that would hide in-place changes.
 */
record SubscriptionSnapshot(List<Item> items)
{
    static SubscriptionSnapshot snapshot(JsonMapper jsonMapper, List<? extends Meta<?>> items, SubscriptionSnapshot previous)
    {
        ImmutableList.Builder<Item> snapshots = ImmutableList.builderWithExpectedSize(items.size());
        for (int index = 0; index < items.size(); index++) {
            Object previousValue = previous != null && index < previous.items.size() ? previous.items.get(index).value : null;
            snapshots.add(snapshotItem(jsonMapper, items.get(index), previousValue));
        }
        return new SubscriptionSnapshot(snapshots.build());
    }

    private static Item snapshotItem(JsonMapper jsonMapper, Meta<?> item, Object previousValue)
    {
        Object value = switch (item) {
            case Tool tool -> new ToolSnapshot(
                    tool.name(),
                    tool.description(),
                    tool.title(),
                    jsonMapper.writeValueAsString(tool.inputSchema()),
                    tool.outputSchema().map(jsonMapper::writeValueAsString),
                    tool.annotations(),
                    tool.icons());
            case ResourceContents contents -> {
                ResourceSnapshot previous = previousValue instanceof ResourceSnapshot resource ? resource : null;
                yield new ResourceSnapshot(
                        contents.name(),
                        contents.uri(),
                        contents.mimeType(),
                        contents.text().map(text -> fingerprint(text, previous == null ? null : previous.text.orElse(null))),
                        contents.blob().map(blob -> fingerprint(blob, previous == null ? null : previous.blob.orElse(null))));
            }
            // Apart from metadata, these records contain only immutable values.
            case Prompt _, Resource _, ResourceTemplate _ -> item.meta().isEmpty() ? item : item.withMeta(Map.of());
            // Preserve correctness if another model type is added to subscriptions later.
            default -> jsonMapper.writeValueAsString(item);
        };
        return new Item(value, item.meta().map(jsonMapper::writeValueAsString));
    }

    @SuppressWarnings("UnstableApiUsage")
    private static ContentFingerprint fingerprint(String value, ContentFingerprint previous)
    {
        if (previous != null) {
            String previousValue = previous.value.get();
            if (previousValue == value) {
                return previous;
            }
            if (value.equals(previousValue)) {
                return new ContentFingerprint(new WeakReference<>(value), previous.hash);
            }
        }
        // Hash UTF-16 code units in chunks, preserving even unpaired surrogates without
        // allocating an encoded copy of the entire resource or updating the digest per character.
        Hasher hasher = Hashing.sha256().newHasher();
        byte[] buffer = new byte[Math.min(value.length(), 4096) * Character.BYTES];
        for (int offset = 0; offset < value.length(); ) {
            int length = Math.min(value.length() - offset, buffer.length / Character.BYTES);
            for (int index = 0; index < length; index++) {
                char character = value.charAt(offset + index);
                buffer[index * Character.BYTES] = (byte) character;
                buffer[index * Character.BYTES + 1] = (byte) (character >>> Byte.SIZE);
            }
            hasher.putBytes(buffer, 0, length * Character.BYTES);
            offset += length;
        }
        return new ContentFingerprint(new WeakReference<>(value), hasher.hash());
    }

    // Resource providers may produce large, short-lived payloads. Keep only their fingerprints
    // strongly reachable, and reuse a fingerprint while the provider retains the same string.
    private record ContentFingerprint(WeakReference<String> value, HashCode hash)
    {
        @Override
        public boolean equals(Object object)
        {
            return object instanceof ContentFingerprint other && hash.equals(other.hash);
        }

        @Override
        public int hashCode()
        {
            return hash.hashCode();
        }
    }

    private record ResourceSnapshot(
            Optional<String> name,
            String uri,
            String mimeType,
            Optional<ContentFingerprint> text,
            Optional<ContentFingerprint> blob) {}

    private record Item(Object value, Optional<String> meta) {}

    private record ToolSnapshot(
            String name,
            Optional<String> description,
            Optional<String> title,
            String inputSchema,
            Optional<String> outputSchema,
            ToolAnnotations annotations,
            Optional<List<Icon>> icons) {}
}
