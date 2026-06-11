package io.airlift.api.binding;

import io.opentelemetry.context.Context;
import org.glassfish.jersey.server.ManagedAsyncExecutor;
import org.glassfish.jersey.spi.ExecutorServiceProvider;

import java.util.concurrent.ExecutorService;

import static java.util.Objects.requireNonNull;

@ManagedAsyncExecutor
class ApiCancellableRequestExecutorProvider
        implements ExecutorServiceProvider
{
    private final ExecutorService executor;

    ApiCancellableRequestExecutorProvider(ExecutorService executor)
    {
        this.executor = Context.taskWrapping(requireNonNull(executor, "executor is null"));
    }

    @Override
    public ExecutorService getExecutorService()
    {
        return executor;
    }

    @Override
    public void dispose(ExecutorService executorService) {}
}
