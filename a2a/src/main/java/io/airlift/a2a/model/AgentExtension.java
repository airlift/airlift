package io.airlift.a2a.model;

import java.util.Map;
import java.util.Optional;

public record AgentExtension(Optional<String> url, Optional<String> description, Optional<Boolean> required, Optional<Map<String, Object>> params)
{
}
