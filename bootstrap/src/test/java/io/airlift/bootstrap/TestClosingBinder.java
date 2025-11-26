package io.airlift.bootstrap;

import com.google.inject.Injector;
import com.google.inject.Module;
import org.junit.jupiter.api.Test;

import java.io.Closeable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.airlift.bootstrap.ClosingBinder.closingBinder;
import static java.util.concurrent.Executors.newCachedThreadPool;
import static org.assertj.core.api.Assertions.assertThat;

public class TestClosingBinder
{
    @Test
    public void testExecutorShutdown()
    {
        com.google.inject.Module module = binder -> {
            binder.bind(ExecutorService.class).toInstance(newCachedThreadPool());
            closingBinder(binder).registerExecutor(ExecutorService.class);
        };
        Injector injector = getInjector(module);

        ExecutorService executorService = injector.getInstance(ExecutorService.class);
        assertThat(executorService.isShutdown()).isFalse();
        stop(injector);
        assertThat(executorService.isShutdown()).isTrue();
    }

    @Test
    public void testCloseableShutdown()
    {
        AtomicBoolean closed = new AtomicBoolean();
        Injector injector = getInjector(binder -> {
            binder.bind(Closeable.class).toInstance(() -> closed.set(true));
            closingBinder(binder).registerCloseable(Closeable.class);
        });

        assertThat(closed.get()).isFalse();
        stop(injector);
        assertThat(closed.get()).isTrue();
    }

    private static Injector getInjector(Module module)
    {
        Bootstrap app = new Bootstrap(module);

        return app.doNotInitializeLogging()
                .quiet()
                .initialize();
    }

    private static void stop(Injector injector)
    {
        LifeCycleManager lifeCycleManager = injector.getInstance(LifeCycleManager.class);
        lifeCycleManager.stop();
    }
}
