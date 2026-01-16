package io.airlift.mcp.sessions;

import java.util.Optional;

import static java.util.Objects.requireNonNull;

@SuppressWarnings("unused")
public sealed interface BlockingResult<T>
{
    record Fulfilled<T>(T value)
            implements BlockingResult<T>
    {
        public Fulfilled
        {
            requireNonNull(value, "value is null");
        }
    }

    record EmptyFulfilled()
            implements BlockingResult<Object>
    {
        public static final EmptyFulfilled INSTANCE = new EmptyFulfilled();
    }

    record TimedOut()
            implements BlockingResult<Object>
    {
        public static final TimedOut INSTANCE = new TimedOut();
    }

    @SuppressWarnings("unchecked")
    static <T> BlockingResult<T> timedOut()
    {
        return (BlockingResult<T>) TimedOut.INSTANCE;
    }

    @SuppressWarnings("unchecked")
    static <T> BlockingResult<T> fulfilled(Optional<T> maybeValue)
    {
        return maybeValue.map(value -> (BlockingResult<T>) new Fulfilled<>(value))
                .orElse((BlockingResult<T>) EmptyFulfilled.INSTANCE);
    }
}
