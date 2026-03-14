package io.airlift.a2a.model;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public record Artifact(String artifactId, Optional<String> name, Optional<String> description, List<Part> parts, Optional<List<String>> extensions, Optional<Map<String, Object>> metadata)
        implements Metadata
{
}
