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
        boolean waitForKey(Duration maxWait)
                throws InterruptedException;
    }

    public static <T> boolean waitForCondition(SessionController sessionController, SessionId sessionId, SessionValueKey<T> key, Duration timeout, Function<Optional<T>, Boolean> condition, WaitProc waitProc)
            throws InterruptedException
    {
        boolean result = false;

        long timeoutMsRemaining = timeout.toMillis();
        while (timeoutMsRemaining > 0) {
            Stopwatch stopwatch = Stopwatch.createStarted();

            Optional<T> value = sessionController.getSessionValue(sessionId, key);
            if (condition.apply(value)) {
                result = true;
                break;
            }

            timeoutMsRemaining -= stopwatch.elapsed().toMillis();
            if (timeoutMsRemaining > 0) {
                stopwatch.reset().start();
                if (!waitProc.waitForKey(Duration.ofMillis(timeoutMsRemaining))) {
                    break;
                }
                timeoutMsRemaining -= stopwatch.elapsed().toMillis();
            }
        }

        return result;
    }
}
