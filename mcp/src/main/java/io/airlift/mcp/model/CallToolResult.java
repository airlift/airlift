package io.airlift.mcp.model;

public sealed interface CallToolResult
        permits CompleteToolResult, IncompleteToolResult
{
}
