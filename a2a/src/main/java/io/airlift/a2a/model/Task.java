package io.airlift.a2a.model;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public record Task(String id, Optional<String> contextId, TaskStatus status, Optional<List<Message>> history, Optional<List<Artifact>> artifacts, Optional<Map<String, Object>> metadata)
        implements Metadata
{
}
