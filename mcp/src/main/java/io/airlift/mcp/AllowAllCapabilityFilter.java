package io.airlift.mcp;

import io.airlift.mcp.McpIdentity.Authenticated;
import io.airlift.mcp.model.CompleteReference;
import io.airlift.mcp.model.Prompt;
import io.airlift.mcp.model.Resource;
import io.airlift.mcp.model.ResourceTemplate;
import io.airlift.mcp.model.Tool;

class AllowAllCapabilityFilter
        implements McpCapabilityFilter
{
    @Override
    public boolean isAllowed(Authenticated<?> identity, Tool tool)
    {
        return true;
    }

    @Override
    public boolean isAllowed(Authenticated<?> identity, Prompt prompt)
    {
        return true;
    }

    @Override
    public boolean isAllowed(Authenticated<?> identity, Resource resource)
    {
        return true;
    }

    @Override
    public boolean isAllowed(Authenticated<?> identity, ResourceTemplate resourceTemplate)
    {
        return true;
    }

    @Override
    public boolean isAllowed(Authenticated<?> identity, CompleteReference reference)
    {
        return true;
    }
}
