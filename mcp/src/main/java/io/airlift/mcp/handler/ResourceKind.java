package io.airlift.mcp.handler;

public enum ResourceKind
{
    RESOURCE,
    SKILL;

    public boolean isSkill()
    {
        return this != RESOURCE;
    }
}
