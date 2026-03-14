package io.airlift.a2a.internal;

import com.google.inject.Inject;
import io.airlift.a2a.A2AProtocolVersion;
import io.airlift.a2a.A2aRequestContext;
import io.airlift.a2a.AgentCardSupplier;
import io.airlift.a2a.model.AgentCard;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static java.util.Objects.requireNonNull;

@Path(".well-known/agent-card.json")
public class AgentCardResource
{
    private final AgentCardSupplier agentCardSupplier;
    private final A2AProtocolVersion protocolVersion;

    @Inject
    public AgentCardResource(AgentCardSupplier agentCardSupplier, A2AProtocolVersion protocolVersion)
    {
        this.agentCardSupplier = requireNonNull(agentCardSupplier, "agentCardSupplier is null");
        this.protocolVersion = requireNonNull(protocolVersion, "protocolVersion is null");
    }

    @GET
    @Produces(APPLICATION_JSON)
    public Object getAgentCard()
    {
        A2aRequestContext a2aRequestContext = new A2aRequestContext() {};   // TODO
        AgentCard agentCard = agentCardSupplier.agentCard(a2aRequestContext);

        return switch (protocolVersion) {
            case V_0_3_0 -> agentCard.toV03();
            case V_1_0_0 -> agentCard;
        };
    }
}
