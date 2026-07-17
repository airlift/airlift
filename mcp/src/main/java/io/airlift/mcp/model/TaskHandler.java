package io.airlift.mcp.model;

public interface TaskHandler
{
    TaskHandlerResult run()
            throws Exception;
}
