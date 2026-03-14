package io.airlift.a2a.model;

import io.airlift.a2a.model.v0_3_0.AgentCapabilitiesV03;

import java.util.List;
import java.util.Optional;

public record AgentCapabilities(boolean streaming, boolean pushNotifications, Optional<List<AgentExtension>> extensions, boolean extendedAgentCard)
{
    public AgentCapabilitiesV03 toV03()
    {
        return new  AgentCapabilitiesV03(streaming, pushNotifications, pushNotifications, Optional.empty());    // TODO extensions
    }
}
