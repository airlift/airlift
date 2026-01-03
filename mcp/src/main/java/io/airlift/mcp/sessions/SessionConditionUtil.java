package io.airlift.mcp.sessions;

import com.google.common.base.Stopwatch;

import java.time.Duration;
import java.util.Optional;
import java.util.function.Function;

public class SessionConditionUtil
{
    private SessionConditionUtil() {}

    public interface WaitProc
    {
        void waitForKey(Duration maxWait)
                throws InterruptedException;
    }

    public static <T> void waitForCondition(SessionController sessionController, SessionId sessionId, SessionValueKey<T> key, Duration timeout, Function<Optional<T>, Boolean> condition, WaitProc waitProc)
            throws InterruptedException
    {
        long timeoutMsRemaining = timeout.toMillis();
        while (timeoutMsRemaining > 0) {
            Stopwatch stopwatch = Stopwatch.createStarted();

            Optional<T> value = sessionController.getSessionValue(sessionId, key);
            if (condition.apply(value)) {
                break;
            }

            timeoutMsRemaining -= stopwatch.elapsed().toMillis();

            if (timeoutMsRemaining > 0) {
                stopwatch.reset().start();
                waitProc.waitForKey(Duration.ofMillis(timeoutMsRemaining));
                timeoutMsRemaining -= stopwatch.elapsed().toMillis();
            }
        }
    }
}
