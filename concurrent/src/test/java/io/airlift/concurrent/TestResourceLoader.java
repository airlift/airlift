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

import com.google.common.io.Files;
import io.airlift.concurrent.ResourceLoader.FileLoader;
import io.airlift.concurrent.ResourceLoader.SimpleRecurringTaskRunner;
import io.airlift.testing.TempFile;
import io.airlift.units.Duration;
import org.testng.annotations.Test;

import java.io.File;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static io.airlift.testing.Assertions.assertGreaterThan;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;

public class TestResourceLoader
{
    @Test
    public void testManualRefresh()
            throws Exception
    {
        try (TempFile tempFile = new TempFile()) {
            Files.write("data".getBytes(UTF_8), tempFile.file());

            ResourceLoader<String> loader = ResourceLoader.builder(file -> Files.asCharSource(file, UTF_8).read())
                    .setName("test")
                    .setFile(tempFile.file())
                    .build();

            assertEquals(loader.getValue(), "data");

            Files.write("new-data".getBytes(UTF_8), tempFile.file());
            assertEquals(loader.getValue(), "data");

            loader.refreshNow();
            assertEquals(loader.getValue(), "new-data");

            loader.stop();
        }
    }

    @Test
    public void testBackgroundRefresh()
            throws Exception
    {
        try (TempFile tempFile = new TempFile()) {
            Files.write("data".getBytes(UTF_8), tempFile.file());

            Duration refreshDuration = new Duration(123, SECONDS);
            TestingRecurringTaskRunner taskRunner = new TestingRecurringTaskRunner(refreshDuration);
            ResourceLoader<String> loader = ResourceLoader.builder(file -> Files.asCharSource(file, UTF_8).read())
                    .setName("test")
                    .setFile(tempFile.file())
                    .setRefreshPeriod(refreshDuration)
                    .setRecurringTaskRunner(taskRunner)
                    .build();

            assertEquals(loader.getValue(), "data");

            Files.write("new-data".getBytes(UTF_8), tempFile.file());
            assertEquals(loader.getValue(), "data");

            taskRunner.runTask();
            assertEquals(loader.getValue(), "new-data");

            loader.stop();
            assertTrue(taskRunner.isStopped(), "Expected task to be stopped");

            assertEquals(loader.getValue(), "new-data");
        }
    }

    @Test
    public void testBuiltinRefresh()
            throws Exception
    {
        try (TempFile tempFile = new TempFile()) {
            Files.write("data".getBytes(UTF_8), tempFile.file());

            LoaderWaiter loaderWaiter = new LoaderWaiter();
            Duration refreshDuration = new Duration(10, MILLISECONDS);
            ResourceLoader<Long> loader = ResourceLoader.builder(loaderWaiter)
                    .setName("test")
                    .setFile(tempFile.file())
                    .setRefreshPeriod(refreshDuration)
                    .build();

            long initialValue = loader.getValue();
            assertGreaterThan(initialValue, 0L);

            // verify load called in background
            loaderWaiter.waitForLoad();
            long firstWaitValue = loader.getValue();
            assertGreaterThan(firstWaitValue, initialValue);

            // verify again
            loaderWaiter.waitForLoad();
            long secondWaitValue = loader.getValue();
            assertGreaterThan(secondWaitValue, firstWaitValue);

            loader.stop();
        }
    }

    private static class LoaderWaiter
            implements FileLoader<Long>
    {
        private final AtomicLong loadCount = new AtomicLong();
        private final AtomicReference<CompletableFuture<?>> waiter = new AtomicReference<>();

        @Override
        public Long loadFile(File file)
        {
            CompletableFuture<?> future = waiter.getAndSet(null);
            if (future != null) {
                future.complete(null);
            }
            return loadCount.incrementAndGet();
        }

        public void waitForLoad()
                throws Exception
        {
            CompletableFuture<Object> future = new CompletableFuture<>();
            CompletableFuture<?> currentWaiter = waiter.getAndSet(future);
            assertNull(currentWaiter);
            future.get(30, SECONDS);
        }
    }

    @Test
    public void testRefreshException()
            throws Exception
    {
        try (TempFile tempFile = new TempFile()) {
            Files.write("initial".getBytes(UTF_8), tempFile.file());

            // exception from construction throws directly
            assertThrows(
                    SecurityException.class,
                    () -> ResourceLoader.builder(file -> { throw new SecurityException(); })
                            .setName("test")
                            .setFile(tempFile.file())
                            .build());

            AtomicReference<Exception> nextException = new AtomicReference<>();
            AtomicReference<Exception> exceptionHandler = new AtomicReference<>();
            Duration refreshDuration = new Duration(123, SECONDS);
            TestingRecurringTaskRunner taskRunner = new TestingRecurringTaskRunner(refreshDuration);
            ResourceLoader<String> loader = ResourceLoader.builder(
                    file -> {
                        Exception exception = nextException.get();
                        if (exception != null) {
                            throw exception;
                        }
                        return Files.asCharSource(file, UTF_8).read();
                    })
                    .setName("test")
                    .setFile(tempFile.file())
                    .setRefreshPeriod(refreshDuration)
                    .setRecurringTaskRunner(taskRunner)
                    .setExceptionHandler(exceptionHandler::set)
                    .build();

            assertEquals(loader.getValue(), "initial");

            // manually refresh throws exception directly
            Files.write("manual".getBytes(UTF_8), tempFile.file());
            nextException.set(new NumberFormatException());
            assertThrows(NumberFormatException.class, loader::refreshNow);
            assertNull(exceptionHandler.get());
            assertEquals(loader.getValue(), "initial");

            // verify refresh after exception works
            nextException.set(null);
            exceptionHandler.set(null);
            loader.refreshNow();
            assertNull(exceptionHandler.get());
            assertEquals(loader.getValue(), "manual");

            // background refresh calls exception handler
            Files.write("background".getBytes(UTF_8), tempFile.file());
            nextException.set(new SecurityException());
            taskRunner.runTask();
            assertSame(exceptionHandler.get(), nextException.get());
            assertEquals(loader.getValue(), "manual");

            // verify refresh after exception works
            nextException.set(null);
            exceptionHandler.set(null);
            taskRunner.runTask();
            assertNull(exceptionHandler.get());
            assertEquals(loader.getValue(), "background");

            loader.stop();
        }
    }

    private static class TestingRecurringTaskRunner
            implements SimpleRecurringTaskRunner
    {
        private final Duration expectedFixDelay;
        private Runnable runnable;
        private boolean stopped;

        public TestingRecurringTaskRunner(Duration expectedFixDelay)
        {
            this.expectedFixDelay = requireNonNull(expectedFixDelay, "expectedFixDelay is null");
        }

        @Override
        public Runnable scheduleTask(Runnable runnable, Duration fixedDelay)
        {
            assertNotNull(runnable, "Runnable already set");
            assertEquals(fixedDelay, expectedFixDelay);
            this.runnable = runnable;
            return () -> stopped = true;
        }

        public void runTask()
        {
            assertNotNull(runnable, "Runnable already set");
            runnable.run();
        }

        public boolean isStopped()
        {
            return stopped;
        }
    }
}
