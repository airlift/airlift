package io.airlift.a2a.model;

import io.airlift.a2a.model.v0_3_0.AgentProviderV03;

public record AgentProvider(String url, String organization)
{
    public AgentProviderV03 toV03()
    {
        return new  AgentProviderV03(url, organization);
    }
}
