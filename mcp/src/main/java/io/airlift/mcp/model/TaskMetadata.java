package io.airlift.mcp.model;

import java.util.OptionalInt;

public interface TaskMetadata
{
    TaskMetadata EMPTY = OptionalInt::empty;

    OptionalInt ttl();
}
