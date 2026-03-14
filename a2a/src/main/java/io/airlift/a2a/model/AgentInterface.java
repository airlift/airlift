package io.airlift.a2a.model;

import java.util.Optional;

public record AgentInterface(String url, String protocolBinding, Optional<String> tenant, String protocolVersion)
        implements Tenant
{
}
