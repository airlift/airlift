package io.airlift.a2a.model.v0_3_0;

import java.util.Map;
import java.util.Optional;

public record AgentExtensionV03(String uri, Optional<String> description, boolean required, Optional<Map<String, Object>> params)
{
}
