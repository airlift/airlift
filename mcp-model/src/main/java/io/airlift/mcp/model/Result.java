package io.airlift.mcp.model;

public sealed interface Result
        permits GetPromptResult,
                InputRequests,
                ReadResourceResult,
                ToolResult {}
