package io.airlift.mcp.model;

public sealed interface GetPromptResponse
        extends Result
        permits GetPromptResult, InputRequests
{
}
