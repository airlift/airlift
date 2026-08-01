package io.airlift.mcp.model;

public enum TaskErrorState
{
    NONE,
    FAILED,
    CANCELLATION_REQUESTED,
    CANCELED,
}
