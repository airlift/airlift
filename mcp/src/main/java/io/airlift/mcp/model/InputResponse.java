package io.airlift.mcp.model;

public sealed interface InputResponse
        permits CreateMessageResult, ElicitResult, ListRootsResult
{
}
