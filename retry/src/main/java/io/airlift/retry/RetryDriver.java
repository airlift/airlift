package io.airlift.retry;

import com.google.common.collect.ImmutableList;
import io.airlift.log.Logger;
import io.airlift.units.Duration;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.util.Objects.requireNonNull;

public class RetryDriver
{
    private static final Logger log = Logger.get(RetryDriver.class);

    private static final int DEFAULT_RETRY_ATTEMPTS = 10;
    private static final Duration DEFAULT_SLEEP_TIME = Duration.valueOf("1s");
    private static final Duration DEFAULT_MAX_RETRY_TIME = Duration.valueOf("30s");
    private static final double DEFAULT_SCALE_FACTOR = 2.0;

    private final int maxAttempts;
    private final Duration minSleepTime;
    private final Duration maxSleepTime;
    private final double scaleFactor;
    private final Duration maxRetryTime;
    private final Function<Exception, Exception> exceptionMapper;
    private final List<Predicate<Exception>> stopConditions;
    private final Optional<Runnable> retryRunnable;

    private RetryDriver(
            int maxAttempts,
            Duration minSleepTime,
            Duration maxSleepTime,
            double scaleFactor,
            Duration maxRetryTime,
            Function<Exception, Exception> exceptionMapper,
            List<Predicate<Exception>> stopConditions,
            Optional<Runnable> retryRunnable)
    {
        this.maxAttempts = maxAttempts;
        this.minSleepTime = minSleepTime;
        this.maxSleepTime = maxSleepTime;
        this.scaleFactor = scaleFactor;
        this.maxRetryTime = maxRetryTime;
        this.exceptionMapper = exceptionMapper;
        this.stopConditions = stopConditions;
        this.retryRunnable = retryRunnable;
    }

    private RetryDriver()
    {
        this(DEFAULT_RETRY_ATTEMPTS,
                DEFAULT_SLEEP_TIME,
                DEFAULT_SLEEP_TIME,
                DEFAULT_SCALE_FACTOR,
                DEFAULT_MAX_RETRY_TIME,
                Function.identity(),
                ImmutableList.of(),
                Optional.empty());
    }

    public static RetryDriver retry()
    {
        return new RetryDriver();
    }

    public final RetryDriver maxAttempts(int maxAttempts)
    {
        return new RetryDriver(maxAttempts, minSleepTime, maxSleepTime, scaleFactor, maxRetryTime, exceptionMapper, stopConditions, retryRunnable);
    }

    public final RetryDriver exponentialBackoff(Duration minSleepTime, Duration maxSleepTime, Duration maxRetryTime, double scaleFactor)
    {
        return new RetryDriver(maxAttempts, minSleepTime, maxSleepTime, scaleFactor, maxRetryTime, exceptionMapper, stopConditions, retryRunnable);
    }

    public final RetryDriver onRetry(Runnable retryRunnable)
    {
        return new RetryDriver(maxAttempts, minSleepTime, maxSleepTime, scaleFactor, maxRetryTime, exceptionMapper, stopConditions, Optional.ofNullable(retryRunnable));
    }

    public final RetryDriver exceptionMapper(Function<Exception, Exception> exceptionMapper)
    {
        return new RetryDriver(maxAttempts, minSleepTime, maxSleepTime, scaleFactor, maxRetryTime, exceptionMapper, stopConditions, retryRunnable);
    }

    @SafeVarargs
    public final RetryDriver stopOn(Class<? extends Exception>... classes)
    {
        requireNonNull(classes, "classes is null");
        List<Predicate<Exception>> classesAsPredicates = Stream.of(classes)
                .map(clazz -> (Predicate<Exception>) e -> clazz.isInstance(e))
                .collect(toImmutableList());
        List<Predicate<Exception>> stopConditions = ImmutableList.<Predicate<Exception>>builder()
                .addAll(this.stopConditions)
                .addAll(classesAsPredicates)
                .build();

        return new RetryDriver(maxAttempts, minSleepTime, maxSleepTime, scaleFactor, maxRetryTime, exceptionMapper, stopConditions, retryRunnable);
    }

    public final RetryDriver stopOn(Predicate<Exception>... stopConditions)
    {
        requireNonNull(stopConditions, "stopConditions is null");
        List<Predicate<Exception>> stopCondittions = ImmutableList.<Predicate<Exception>>builder()
                .addAll(this.stopConditions)
                .add(stopConditions)
                .build();

        return new RetryDriver(maxAttempts, minSleepTime, maxSleepTime, scaleFactor, maxRetryTime, exceptionMapper, stopCondittions, retryRunnable);
    }

    public RetryDriver stopOnIllegalExceptions()
    {
        return stopOn(NullPointerException.class, IllegalStateException.class, IllegalArgumentException.class);
    }

    public <V> V run(String callableName, Callable<V> callable)
            throws Exception
    {
        requireNonNull(callableName, "callableName is null");
        requireNonNull(callable, "callable is null");

        List<Throwable> suppressedExceptions = new ArrayList<>();
        long startTime = System.nanoTime();
        int attempt = 0;
        while (true) {
            attempt++;

            if (attempt > 1) {
                retryRunnable.ifPresent(Runnable::run);
            }

            try {
                return callable.call();
            }
            catch (Exception e) {
                e = exceptionMapper.apply(e);
                for (Predicate<Exception> predicate : stopConditions) {
                    if (predicate.test(e)) {
                        addSuppressed(e, suppressedExceptions);
                        throw e;
                    }
                }
                if (attempt >= maxAttempts || Duration.nanosSince(startTime).compareTo(maxRetryTime) >= 0) {
                    addSuppressed(e, suppressedExceptions);
                    throw e;
                }
                log.debug("Failed on executing %s with attempt %d, will retry. Exception: %s", callableName, attempt, e.getMessage());

                suppressedExceptions.add(e);

                int delayInMs = (int) Math.min(minSleepTime.toMillis() * Math.pow(scaleFactor, attempt - 1), maxSleepTime.toMillis());
                int jitter = ThreadLocalRandom.current().nextInt(Math.max(1, (int) (delayInMs * 0.1)));
                try {
                    TimeUnit.MILLISECONDS.sleep(delayInMs + jitter);
                }
                catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    Exception exception = new RuntimeException(ie);
                    addSuppressed(exception, suppressedExceptions);
                    throw exception;
                }
            }
        }
    }

    private static void addSuppressed(Exception exception, List<Throwable> suppressedExceptions)
    {
        for (Throwable suppressedException : suppressedExceptions) {
            if (exception != suppressedException) {
                exception.addSuppressed(suppressedException);
            }
        }
    }
}
