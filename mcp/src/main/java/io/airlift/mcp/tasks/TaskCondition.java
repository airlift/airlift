package io.airlift.mcp.tasks;

import java.util.function.Predicate;

public interface TaskCondition
        extends Predicate<TaskAdapter>
{
    TaskCondition isCompleted = task -> task.completedAt().isPresent();

    TaskCondition hasMessage = task -> task.message().isPresent();

    TaskCondition cancellationRequested = TaskAdapter::cancellationRequested;

    static TaskCondition hasResponseWithId(Object responseId)
    {
        return task -> task.responses().containsKey(responseId);
    }
}
