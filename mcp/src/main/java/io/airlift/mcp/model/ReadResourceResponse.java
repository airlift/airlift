package io.airlift.mcp.model;

public sealed interface ReadResourceResponse
        extends Result
        permits ReadResourceResult, InputRequests
{
}
