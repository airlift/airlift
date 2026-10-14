/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.airlift.concurrent;

import com.google.common.annotations.VisibleForTesting;
import io.airlift.log.Logger;
import io.airlift.units.Duration;

import javax.annotation.PreDestroy;
import javax.annotation.concurrent.GuardedBy;

import java.io.File;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static com.google.common.base.Preconditions.checkArgument;
import static io.airlift.concurrent.Threads.daemonThreadsNamed;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.Executors.newSingleThreadScheduledExecutor;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

public final class ResourceLoader<T>
{
    private static final Logger log = Logger.get(ResourceLoader.class);

    private final File file;
    private final FileLoader<T> fileLoader;
    private final Duration refreshPeriod;
    private final Consumer<Exception> exceptionHandler;
    private final AtomicReference<Runnable> stopRefresh = new AtomicReference<>();

    @GuardedBy("this")
    private T value;

    private static <T> ResourceLoader<T> createResourceLoader(
            File file,
            FileLoader<T> fileLoader,
            Duration refreshPeriod,
            SimpleRecurringTaskRunner recurringTaskRunner,
            Consumer<Exception> exceptionHandler)
            throws Exception
    {
        requireNonNull(recurringTaskRunner, "recurringTaskRunner is null");
        ResourceLoader<T> resourceLoader = new ResourceLoader<>(
                fileLoader,
                file,
                refreshPeriod,
                exceptionHandler);
        resourceLoader.start(recurringTaskRunner);
        return resourceLoader;
    }

    private ResourceLoader(FileLoader<T> fileLoader, File file, Duration refreshPeriod, Consumer<Exception> exceptionHandler)
            throws Exception
    {
        this.fileLoader = requireNonNull(fileLoader, "fileLoader is null");
        this.file = requireNonNull(file, "file is null");
        this.refreshPeriod = requireNonNull(refreshPeriod, "refreshPeriod is null");
        this.exceptionHandler = requireNonNull(exceptionHandler, "exceptionHandler is null");
        refreshNow();
    }

    private void start(SimpleRecurringTaskRunner recurringTaskRunner)
    {
        stopRefresh.set(recurringTaskRunner.scheduleTask(this::refreshInternal, refreshPeriod));
    }

    @PreDestroy
    public void stop()
    {
        Runnable stopRefresh = this.stopRefresh.getAndSet(null);
        if (stopRefresh != null) {
            stopRefresh.run();
        }
    }

    public synchronized T getValue()
    {
        return value;
    }

    public synchronized void refreshNow()
            throws Exception
    {
        value = fileLoader.loadFile(file);
    }

    private void refreshInternal()
    {
        try {
            refreshNow();
        }
        catch (Exception e) {
            exceptionHandler.accept(e);
        }
    }

    public interface FileLoader<T>
    {
        T loadFile(File file)
                throws Exception;
    }

    public interface SimpleRecurringTaskRunner
    {
        Runnable scheduleTask(Runnable runnable, Duration fixedDelay);
    }

    private static class InternalScheduledRecurringTaskRunner
            implements SimpleRecurringTaskRunner
    {
        private final String name;

        private InternalScheduledRecurringTaskRunner(String name)
        {
            this.name = requireNonNull(name, "name is null");
        }

        @Override
        public Runnable scheduleTask(Runnable runnable, Duration fixedDelay)
        {
            ScheduledExecutorService executor = newSingleThreadScheduledExecutor(daemonThreadsNamed(name + "-background-loader"));
            long fixedDelayNanos = fixedDelay.roundTo(NANOSECONDS);
            executor.scheduleAtFixedRate(runnable, fixedDelayNanos, fixedDelayNanos, NANOSECONDS);
            return executor::shutdownNow;
        }
    }

    private static class NoOpScheduledRecurringTaskRunner
            implements SimpleRecurringTaskRunner
    {
        @Override
        public Runnable scheduleTask(Runnable runnable, Duration fixedDelay)
        {
            return () -> {};
        }
    }

    public static <T> ResourceLoaderBuilder<T> builder(FileLoader<T> fileLoader)
    {
        return new ResourceLoaderBuilder<>(fileLoader);
    }

    public static final class ResourceLoaderBuilder<T>
    {
        private final FileLoader<T> fileLoader;
        private String name;
        private File file;
        private Duration refreshPeriod = new Duration(0, NANOSECONDS);
        private SimpleRecurringTaskRunner recurringTaskRunner;
        private Consumer<Exception> exceptionHandler = exception -> log.error("Error refreshing " + name, exception);

        private ResourceLoaderBuilder(FileLoader<T> fileLoader)
        {
            this.fileLoader = requireNonNull(fileLoader, "fileLoader is null");
        }

        public ResourceLoaderBuilder<T> setName(String name)
        {
            this.name = requireNonNull(name, "name is null");
            return this;
        }

        public ResourceLoaderBuilder<T> setFile(File file)
        {
            this.file = requireNonNull(file, "file is null");
            return this;
        }

        public ResourceLoaderBuilder<T> setRefreshPeriod(Duration refreshPeriod)
        {
            this.refreshPeriod = requireNonNull(refreshPeriod, "refreshPeriod is null");
            return this;
        }

        @VisibleForTesting
        ResourceLoaderBuilder<T> setRecurringTaskRunner(SimpleRecurringTaskRunner recurringTaskRunner)
        {
            this.recurringTaskRunner = requireNonNull(recurringTaskRunner, "recurringTaskRunner is null");
            return this;
        }

        public ResourceLoaderBuilder<T> setExceptionHandler(Consumer<Exception> exceptionHandler)
        {
            this.exceptionHandler = exceptionHandler;
            return this;
        }

        public ResourceLoader<T> build()
                throws Exception
        {
            checkArgument(name != null, "name not set");
            checkArgument(file != null, "file not set");

            SimpleRecurringTaskRunner recurringTaskRunner = this.recurringTaskRunner;
            if (recurringTaskRunner == null) {
                if (refreshPeriod.roundTo(NANOSECONDS) > 0) {
                    recurringTaskRunner = new InternalScheduledRecurringTaskRunner(name);
                }
                else {
                    recurringTaskRunner = new NoOpScheduledRecurringTaskRunner();
                }
            }
            return createResourceLoader(
                    file,
                    fileLoader,
                    refreshPeriod,
                    recurringTaskRunner,
                    exceptionHandler);
        }
    }
}
