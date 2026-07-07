package io.airlift.mcp.model;

public sealed interface Result
        permits CallToolResult,
                CompleteTaskResult,
                CreateTaskResult,
                EmptyResult,
                InputRequiredTaskResult {}
