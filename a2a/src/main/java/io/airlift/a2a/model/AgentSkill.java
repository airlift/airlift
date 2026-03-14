package io.airlift.a2a.model;

import io.airlift.a2a.model.v0_3_0.AgentSkillV03;

import java.util.List;
import java.util.Optional;

public record AgentSkill(String id, String name, String description, List<String> tags, Optional<List<String>> examples, Optional<List<String>> inputModes, Optional<List<String>> outputModes, Optional<Object> securityRequirements /* TODO */)
{
    public AgentSkillV03 toV03()
    {
        return new AgentSkillV03(id, name, description, tags, examples, inputModes, outputModes);
    }
}
