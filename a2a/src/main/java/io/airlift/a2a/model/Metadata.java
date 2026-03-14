package io.airlift.a2a.model;

import java.util.Map;
import java.util.Optional;

public interface Metadata
{
    Optional<Map<String, Object>> metadata();
}
