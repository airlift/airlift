package io.airlift.airship.shared;

import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.CharStreams;
import com.google.common.io.Closeables;
import com.google.common.primitives.Ints;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListenableFutureTask;
import io.airlift.units.Duration;

import javax.annotation.concurrent.Immutable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Immutable
public class Command
{
    private static final ImmutableSet<Integer> DEFAULT_SUCCESSFUL_EXIT_CODES = ImmutableSet.of(0);
    private static final File DEFAULT_DIRECTORY = new File(".").getAbsoluteFile();
    private static final Duration DEFAULT_TIME_LIMIT = new Duration(365, TimeUnit.DAYS);

    private final List<String> command;
    private final Set<Integer> successfulExitCodes;
    private final File directory;
    private final Map<String, String> environment;
    private final Duration timeLimit;

    public Command(String... command)
    {
        this(ImmutableList.copyOf(command), DEFAULT_SUCCESSFUL_EXIT_CODES, DEFAULT_DIRECTORY, ImmutableMap.<String, String>of(), DEFAULT_TIME_LIMIT);
    }

    public Command(List<String> command, Set<Integer> successfulExitCodes, File directory, Map<String, String> environment, Duration timeLimit)
    {
        Preconditions.checkNotNull(command, "command is null");
        Preconditions.checkArgument(!command.isEmpty(), "command is empty");
        Preconditions.checkNotNull(successfulExitCodes, "successfulExitCodes is null");
        Preconditions.checkArgument(!successfulExitCodes.isEmpty(), "successfulExitCodes is empty");
        Preconditions.checkNotNull(directory, "directory is null");
        Preconditions.checkNotNull(timeLimit, "timeLimit is null");

        this.command = ImmutableList.copyOf(command);

        // these have default so are required
        this.successfulExitCodes = ImmutableSet.copyOf(successfulExitCodes);
        this.directory = directory;
        this.environment = environment;
        this.timeLimit = timeLimit;
    }

    public List<String> getCommand()
    {
        return command;
    }

    public Command addArgs(String... args)
    {
        Preconditions.checkNotNull(args, "args is null");
        return addArgs(ImmutableList.copyOf(args));
    }

    public Command addArgs(Iterable<String> args)
    {
        Preconditions.checkNotNull(args, "args is null");
        ImmutableList.Builder<String> command = ImmutableList.<String>builder().addAll(this.command).addAll(args);
        return new Command(command.build(), successfulExitCodes, directory, environment, timeLimit);
    }

    public Map<String, String> getEnvironment()
    {
        return environment;
    }

    public Command addEnvironment(String name, String value)
    {
        Preconditions.checkNotNull(name, "name is null");
        Preconditions.checkNotNull(value, "value is null");
        ImmutableMap.Builder<String, String> builder = ImmutableMap.<String, String>builder().putAll(this.environment).put(name, value);
        return new Command(command, successfulExitCodes, directory, builder.build(), timeLimit);
    }

    public Command addEnvironment(Map<String, String> environment)
    {
        Preconditions.checkNotNull(environment, "environment is null");
        ImmutableMap.Builder<String, String> builder = ImmutableMap.<String, String>builder().putAll(this.environment).putAll(environment);
        return new Command(command, successfulExitCodes, directory, builder.build(), timeLimit);
    }

    public Set<Integer> getSuccessfulExitCodes()
    {
        return successfulExitCodes;
    }

    public Command setSuccessfulExitCodes(int... successfulExitCodes)
    {
        Preconditions.checkNotNull(successfulExitCodes, "successfulExitCodes is null");
        return setSuccessfulExitCodes(ImmutableSet.copyOf(Ints.asList(successfulExitCodes)));
    }

    public Command setSuccessfulExitCodes(Set<Integer> successfulExitCodes)
    {
        Preconditions.checkNotNull(successfulExitCodes, "successfulExitCodes is null");
        Preconditions.checkArgument(!successfulExitCodes.isEmpty(), "successfulExitCodes is empty");
        return new Command(command, successfulExitCodes, directory, environment, timeLimit);
    }

    public File getDirectory()
    {
        return directory;
    }

    public Command setDirectory(String directory)
    {
        Preconditions.checkNotNull(directory, "directory is null");
        return setDirectory(new File(directory));
    }

    public Command setDirectory(File directory)
    {
        Preconditions.checkNotNull(directory, "directory is null");
        return new Command(command, successfulExitCodes, directory, environment, timeLimit);
    }

    public Duration getTimeLimit()
    {
        return timeLimit;
    }

    public Command setTimeLimit(double value, TimeUnit timeUnit)
    {
        return setTimeLimit(new Duration(value, timeUnit));
    }

    public Command setTimeLimit(Duration timeLimit)
    {
        Preconditions.checkNotNull(timeLimit, "timeLimit is null");
        return new Command(command, successfulExitCodes, directory, environment, timeLimit);
    }

    public int execute(Executor executor)
            throws CommandFailedException
    {
        Preconditions.checkNotNull(executor, "executor is null");
        Preconditions.checkNotNull(command, "command is null");

        ProcessCallable processCallable = new ProcessCallable(this, executor);
        Future<Integer> future = submit(executor, processCallable);

        try {
            Integer result = future.get((long) timeLimit.toMillis(), TimeUnit.MILLISECONDS);
            return result;
        }
        catch (ExecutionException e) {
            Throwables.propagateIfPossible(e.getCause(), CommandFailedException.class);
            Throwable cause = e.getCause();
            if (cause == null) {
                cause = e;
            }
            throw new CommandFailedException(this, "unexpected exception", cause);
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CommandFailedException(this, "interrupted", e);
        }
        catch (TimeoutException e) {
            future.cancel(true);
            throw new CommandTimeoutException(this);
        }
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        Command other = (Command) o;

        if (!command.equals(other.command)) {
            return false;
        }
        if (!directory.equals(other.directory)) {
            return false;
        }
        if (!successfulExitCodes.equals(other.successfulExitCodes)) {
            return false;
        }
        if (!timeLimit.equals(other.timeLimit)) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode()
    {
        int result = command.hashCode();
        result = 31 * result + successfulExitCodes.hashCode();
        result = 31 * result + directory.hashCode();
        result = 31 * result + timeLimit.hashCode();
        return result;
    }

    @Override
    public String toString()
    {
        final StringBuffer sb = new StringBuffer();
        sb.append("Command");
        sb.append("{command=").append(command);
        sb.append(", successfulExitCodes=").append(successfulExitCodes);
        sb.append(", directory=").append(directory);
        sb.append(", timeLimit=").append(timeLimit);
        sb.append('}');
        return sb.toString();
    }

    private static class ProcessCallable implements Callable<Integer>
    {
        private final Command command;
        private final Executor executor;

        public ProcessCallable(Command command, Executor executor)
        {
            this.command = command;
            this.executor = executor;
        }

        @Override
        public Integer call()
                throws CommandFailedException, InterruptedException
        {
            ProcessBuilder processBuilder = new ProcessBuilder(command.getCommand());
            processBuilder.directory(command.getDirectory());
            processBuilder.redirectErrorStream(true);
            processBuilder.environment().putAll(command.getEnvironment());

            // start the process
            final Process process;
            try {
                process = processBuilder.start();
            }
            catch (IOException e) {
                throw new CommandFailedException(command, "failed to start", e);
            }

            OutputProcessor outputProcessor = null;
            try {
                // start the output processor
                outputProcessor = new OutputProcessor(process, executor);
                outputProcessor.start();

                // wait for command to exit
                int exitCode = process.waitFor();

                // validate exit code
                if (!command.getSuccessfulExitCodes().contains(exitCode)) {
                    String out = outputProcessor.getOutput();
                    throw new CommandFailedException(command, exitCode, out);
                }
                return exitCode;
            }
            finally {
                try {
                    process.destroy();
                }
                finally {
                    if (outputProcessor != null) {
                        outputProcessor.destroy();
                    }
                }
            }
        }
    }

    private static class OutputProcessor
    {
        private final InputStream inputStream;
        private final Executor executor;
        private Future<String> outputFuture;

        private OutputProcessor(Process process, Executor executor)
        {
            this.inputStream = process.getInputStream();
            this.executor = executor;
        }

        public void start()
        {
            outputFuture = submit(executor, new Callable<String>()
            {
                @Override
                public String call()
                        throws IOException
                {
                    String out = CharStreams.toString(new InputStreamReader(inputStream, Charsets.UTF_8));
                    return out;
                }
            });
        }

        private String getOutput()
        {
            if (outputFuture != null && !outputFuture.isCancelled()) {
                try {
                    return outputFuture.get();
                }
                catch (Exception ignored) {
                }
            }
            return null;
        }

        private void destroy()
        {
            // close input stream which will normally interrupt the reader
            Closeables.closeQuietly(inputStream);

            if (outputFuture != null) {
                outputFuture.cancel(true);
            }
        }
    }

    private static <T> ListenableFuture<T> submit(Executor executor, Callable<T> task)
    {
        ListenableFutureTask<T> future = ListenableFutureTask.create(task);
        executor.execute(future);
        return future;
    }
}
