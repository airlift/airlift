package io.airlift.mcp.tasks;

import com.fasterxml.jackson.annotation.JsonValue;

import static java.util.Objects.requireNonNull;

record TaskContextIdImpl(@JsonValue String id)
        implements TaskContextId
{
    TaskContextIdImpl
    {
        requireNonNull(id, "id is null");
    }
}
