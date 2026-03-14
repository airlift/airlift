package io.airlift.a2a;

import io.airlift.a2a.model.AgentCard;

public interface AgentCardSupplier
{
    AgentCard agentCard(A2aRequestContext requestContext);
}
