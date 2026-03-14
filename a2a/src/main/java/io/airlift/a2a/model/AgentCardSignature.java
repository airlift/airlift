package io.airlift.a2a.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;
import java.util.Optional;

public record AgentCardSignature(@JsonProperty("protected") String protectedStr, String signature, Optional<Map<String, Object>> header)
{
}
