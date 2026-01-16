package io.airlift.mcp.sessions;

import com.google.common.base.Stopwatch;

import java.time.Duration;
import java.util.Optional;
import java.util.function.Predicate;

import static io.airlift.mcp.sessions.BlockingResult.fulfilled;
import static io.airlift.mcp.sessions.BlockingResult.timedOut;

public class SessionConditionUtil
{
    private SessionConditionUtil() {}

    public interface WaitProc
    {
        void waitForKey(Duration maxWait)
                throws InterruptedException;
    }

    public static <T> BlockingResult<T> waitForCondition(SessionController sessionController, SessionId sessionId, SessionValueKey<T> key, Duration timeout, Predicate<Optional<T>> condition, WaitProc waitProc)
            throws InterruptedException
    {
        long timeoutMsRemaining = timeout.toMillis();
        while (true) {
            Stopwatch stopwatch = Stopwatch.createStarted();

            Optional<T> value = sessionController.getSessionValue(sessionId, key);
            if (condition.test(value)) {
                return fulfilled(value);
            }

            timeoutMsRemaining -= stopwatch.elapsed().toMillis();
            if (timeoutMsRemaining <= 0) {
                return timedOut(timeout);
            }

            stopwatch.reset().start();
            waitProc.waitForKey(Duration.ofMillis(timeoutMsRemaining));
            timeoutMsRemaining -= stopwatch.elapsed().toMillis();
        }
    }
}
