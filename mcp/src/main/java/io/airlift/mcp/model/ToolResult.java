package io.airlift.mcp.model;

public sealed interface ToolResult
        permits CallToolResult,
                ResultTypeWrapper,
                Task {}
