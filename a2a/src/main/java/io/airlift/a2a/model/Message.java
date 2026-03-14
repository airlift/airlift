package io.airlift.a2a.model;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public record Message(
        String messageId,
        Optional<String> contextId,
        Optional<String> taskId,
        Role role,
        List<Part> parts,
        Optional<Map<String, Object>> metadata,
        Optional<List<String>> extensions,
        Optional<List<String>> referenceTaskIds)
        implements Metadata
{
}
