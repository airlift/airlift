package io.airlift.a2a.model;

import io.airlift.a2a.A2AProtocolVersion;
import io.airlift.a2a.model.v0_3_0.AgentCardV03;

import java.util.List;
import java.util.Optional;

import static com.google.common.collect.ImmutableList.toImmutableList;

public record AgentCard(
        String name,
        String description,
        List<AgentInterface> supportedInterfaces,
        Optional<AgentProvider> provider,
        String version,
        Optional<String> documentationUrl,
        AgentCapabilities capabilities,
        Optional<Object> securitySchemes /* TODO */,
        Optional<Object> securityRequirements /* TODO */,
        List<String> defaultInputModes,
        List<String> defaultOutputModes,
        List<AgentSkill> skills,
        Optional<List<AgentCardSignature>> signatures,
        Optional<String> iconUrl)
{
    public AgentCardV03 toV03()
    {
        AgentInterface firstInterface = supportedInterfaces.getFirst();

        return new AgentCardV03(
                A2AProtocolVersion.V_0_3_0.version(),
                name,
                description,
                firstInterface.url(),
                Optional.empty(),
                Optional.empty(),
                iconUrl,
                provider.map(AgentProvider::toV03),
                version,
                documentationUrl,
                capabilities.toV03(),
                defaultInputModes,
                defaultOutputModes,
                skills.stream().map(AgentSkill::toV03).collect(toImmutableList()),
                Optional.empty());  // TODO
    }
}
