package io.airlift.mcp.handler;

public enum ResourceKind
{
    RESOURCE,
    SKILL,
    DYNAMIC_SKILL;

    public boolean isSkill()
    {
        return this != RESOURCE;
    }
}
