package io.airlift.a2a.model.v0_3_0;

import java.util.List;
import java.util.Optional;

// TODO securitySchemes, security, signatures
public record AgentCardV03(
        String protocolVersion,
        String name,
        String description,
        String url,
        Optional<TransportProtocolV03> preferredTransport,
        Optional<List<AgentInterfaceV03>> additionalInterfaces,
        Optional<String> iconUrl,
        Optional<AgentProviderV03> provider,
        String version,
        Optional<String> documentationUrl,
        AgentCapabilitiesV03 capabilities,
        List<String> defaultInputModes,
        List<String> defaultOutputModes,
        List<AgentSkillV03> skills,
        Optional<Boolean> supportsAuthenticatedExtendedCard)
{
}
