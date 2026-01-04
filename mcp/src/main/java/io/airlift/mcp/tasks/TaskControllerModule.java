package io.airlift.mcp.tasks;

import com.google.inject.Binder;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.binder.LinkedBindingBuilder;

import java.util.Optional;
import java.util.function.Consumer;

import static com.google.inject.Scopes.SINGLETON;
import static java.util.Objects.requireNonNull;

public class TaskControllerModule
        implements Module
{
    private final Consumer<LinkedBindingBuilder<TaskContextMapper>> taskContextBinding;

    public TaskControllerModule(Consumer<LinkedBindingBuilder<TaskContextMapper>> taskContextBinding)
    {
        this.taskContextBinding = requireNonNull(taskContextBinding, "taskContextBinding is null");
    }

    @Override
    public void configure(Binder binder)
    {
        taskContextBinding.accept(binder.bind(TaskContextMapper.class));
        binder.bind(TaskController.class).to(TaskControllerImpl.class).in(SINGLETON);
        binder.bind(TaskContextController.class).to(TaskControllerImpl.class).in(SINGLETON);
    }

    @Provides
    @Singleton
    public Optional<TaskController> optionalTaskController(TaskController taskController)
    {
        return Optional.of(taskController);
    }

    @Provides
    @Singleton
    public Optional<TaskContextController> optionalTaskContextController(TaskContextController taskContextController)
    {
        return Optional.of(taskContextController);
    }

    @Provides
    @Singleton
    public Optional<TaskContextMapper> optionalTaskContextMapper(TaskContextMapper taskContextMapper)
    {
        return Optional.of(taskContextMapper);
    }
}
