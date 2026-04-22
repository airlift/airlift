package io.airlift.mcp.model;

public sealed interface CallToolResponse
        extends Result
        permits CallToolResult, InputRequests
{
}
