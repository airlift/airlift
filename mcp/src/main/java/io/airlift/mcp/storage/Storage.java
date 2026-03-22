package io.airlift.mcp.storage;

import java.time.Duration;
import java.util.Optional;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

public interface Storage
{
    void createGroup(String group, Duration ttl);

    boolean groupExists(String group);

    void deleteGroup(String group);

    Optional<String> getValue(String group, String key);

    boolean setValue(String group, String key, String value);

    void deleteValue(String group, String key);

    Optional<String> computeValue(String group, String key, UnaryOperator<Optional<String>> updater);

    void waitForSignal(String group, Duration timeout)
            throws InterruptedException;

    void signalAll(String group);

    Stream<String> groups();

    Stream<String> keys(String group);
}
