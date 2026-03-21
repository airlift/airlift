package io.airlift.mcp.legacy.sessions;

import java.time.Duration;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.TimeoutException;

import static io.airlift.mcp.legacy.sessions.LegacyBlockingResult.EmptyFulfilled.EMPTY_FULFILLED;
import static java.util.Objects.requireNonNull;

public sealed interface LegacyBlockingResult<T>
{
    T get()
            throws Exception;

    record Fulfilled<T>(T value)
            implements LegacyBlockingResult<T>
    {
        public Fulfilled
        {
            requireNonNull(value, "value is null");
        }

        @Override
        public T get()
        {
            return value;
        }
    }

    record EmptyFulfilled()
            implements LegacyBlockingResult<Object>
    {
        static final EmptyFulfilled EMPTY_FULFILLED = new EmptyFulfilled();

        @Override
        public Object get()
                throws Exception
        {
            throw new NoSuchElementException("No value present");
        }
    }

    record TimedOut(Duration timeout)
            implements LegacyBlockingResult<Object>
    {
        @Override
        public Object get()
                throws Exception
        {
            throw new TimeoutException("Timed out after " + timeout);
        }
    }

    @SuppressWarnings("unchecked")
    static <T> LegacyBlockingResult<T> timedOut(Duration timeout)
    {
        return (LegacyBlockingResult<T>) new TimedOut(timeout);
    }

    @SuppressWarnings("unchecked")
    static <T> LegacyBlockingResult<T> fulfilled(Optional<T> maybeValue)
    {
        if (maybeValue.isEmpty()) {
            return (LegacyBlockingResult<T>) EMPTY_FULFILLED;
        }
        return new Fulfilled<>(maybeValue.orElseThrow());
    }
}
