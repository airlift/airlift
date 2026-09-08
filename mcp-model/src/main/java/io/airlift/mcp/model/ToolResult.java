package io.airlift.mcp.model;

public sealed interface ToolResult
        extends Result
        permits CallToolResult, Task {}
