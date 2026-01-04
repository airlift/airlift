package io.airlift.mcp.tasks;

import java.util.function.Predicate;

public interface TaskConditions
{
    Predicate<TaskFacade> isCompleted = task -> task.completedAt().isPresent();

    Predicate<TaskFacade> hasMessage = task -> task.message().isPresent();

    Predicate<TaskFacade> cancellationRequested = TaskFacade::cancellationRequested;

    static Predicate<TaskFacade> hasResponseWithId(Object responseId)
    {
        return task -> task.responses().containsKey(responseId);
    }
}
