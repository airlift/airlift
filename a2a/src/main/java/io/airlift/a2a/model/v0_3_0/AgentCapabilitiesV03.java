package io.airlift.a2a.model.v0_3_0;

import java.util.List;
import java.util.Optional;

public record AgentCapabilitiesV03(boolean streaming, boolean pushNotifications, boolean stateTransitionHistory, Optional<List<AgentExtensionV03>> extensions)
{
}
