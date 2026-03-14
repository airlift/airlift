package io.airlift.a2a.model.v0_3_0;

import java.util.List;
import java.util.Optional;

// TODO security
public record AgentSkillV03(String id, String name, String description, List<String> tags, Optional<List<String>> examples, Optional<List<String>> inputModes, Optional<List<String>> outputModes)
{
}
