package io.airlift.mcp.sessions;

import java.time.Duration;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.TimeoutException;

import static io.airlift.mcp.sessions.BlockingResult.EmptyFulfilled.EMPTY_FULFILLED;
import static java.util.Objects.requireNonNull;

public sealed interface BlockingResult<T>
{
    T get()
            throws Exception;

    record Fulfilled<T>(T value)
            implements BlockingResult<T>
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
            implements BlockingResult<Object>
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
            implements BlockingResult<Object>
    {
        @Override
        public Object get()
                throws Exception
        {
            throw new TimeoutException("Timed out after " + timeout);
        }
    }

    static <T> BlockingResult<T> timedOut(Duration timeout)
    {
        return (BlockingResult<T>) new TimedOut(timeout);
    }

    static <T> BlockingResult<T> fulfilled(Optional<T> maybeValue)
    {
        if (maybeValue.isEmpty()) {
            return (BlockingResult<T>) EMPTY_FULFILLED;
        }
        return new Fulfilled<>(maybeValue.orElseThrow());
    }
}
