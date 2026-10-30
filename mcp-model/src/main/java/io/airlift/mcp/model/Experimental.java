package io.airlift.mcp.model;

import java.util.Map;
import java.util.Optional;

public interface Experimental
{
    Optional<Map<String, Object>> experimental();
}
