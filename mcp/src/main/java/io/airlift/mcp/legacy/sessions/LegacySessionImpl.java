package io.airlift.mcp.legacy.sessions;

import com.google.common.base.Stopwatch;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

import static io.airlift.mcp.legacy.sessions.LegacyBlockingResult.fulfilled;
import static io.airlift.mcp.legacy.sessions.LegacyBlockingResult.timedOut;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

class LegacySessionImpl
        implements LegacySession
{
    private final LegacySessionId sessionId;
    private final Map<LegacySessionValueKey<?>, Object> values;
    private final Signal signal;
    private final BooleanSupplier isValidProc;

    LegacySessionImpl(LegacySessionId sessionId, Map<LegacySessionValueKey<?>, Object> values, Signal signal, BooleanSupplier isValidProc)
    {
        this.sessionId = requireNonNull(sessionId, "sessionId is null");
        this.values = requireNonNull(values, "values is null");     // do not copy
        this.signal = requireNonNull(signal, "signal is null");
        this.isValidProc = requireNonNull(isValidProc, "isValidProc is null");
    }

    @Override
    public LegacySessionId sessionId()
    {
        return sessionId;
    }

    @Override
    public boolean isValid()
    {
        return isValidProc.getAsBoolean();
    }

    @Override
    public <T> Optional<T> getValue(LegacySessionValueKey<T> key)
    {
        return Optional.ofNullable(values.get(key))
                .map(key.type()::cast);
    }

    @Override
    public <T> void setValue(LegacySessionValueKey<T> key, T value)
    {
        values.put(key, value);
        signal.signalAll();
    }

    @Override
    public <T> void deleteValue(LegacySessionValueKey<T> key)
    {
        values.remove(key);
        signal.signalAll();
    }

    @Override
    public <T> Optional<T> computeValue(LegacySessionValueKey<T> key, UnaryOperator<Optional<T>> updater)
    {
        Object computed = values.compute(key, (_, existingValue) -> {
            Optional<T> existing = Optional.ofNullable(existingValue).map(key.type()::cast);
            return updater.apply(existing).orElse(null);
        });
        signal.signalAll();

        return Optional.ofNullable(computed).map(key.type()::cast);
    }

    @Override
    public <T> LegacyBlockingResult<T> blockUntil(LegacySessionValueKey<T> key, Duration timeout, Predicate<Optional<T>> condition)
            throws InterruptedException
    {
        long timeoutMsRemaining = timeout.toMillis();
        while (true) {
            Stopwatch stopwatch = Stopwatch.createStarted();

            Optional<T> value = getValue(key);
            if (condition.test(value)) {
                return fulfilled(value);
            }

            timeoutMsRemaining -= stopwatch.elapsed().toMillis();
            if (timeoutMsRemaining <= 0) {
                return timedOut(timeout);
            }

            stopwatch.reset().start();
            signal.waitForSignal(timeoutMsRemaining, MILLISECONDS);
            timeoutMsRemaining -= stopwatch.elapsed().toMillis();
        }
    }

    @Override
    public void signalAll()
    {
        signal.signalAll();
    }
}
