package io.airlift.mcp.model;

public sealed interface InputRequest
        permits CreateMessageRequest, ElicitRequestForm, ElicitRequestUrl, ListRootsRequest
{
}
