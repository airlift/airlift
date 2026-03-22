package io.airlift.mcp.storage;

import java.time.Duration;
import java.util.Optional;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

public class DisabledStorage
        implements Storage
{
    @Override
    public void createGroup(String group, Duration ttl)
    {
        throw new UnsupportedOperationException("Storage is disabled");
    }

    @Override
    public void deleteGroup(String group)
    {
        // NOP
    }

    @Override
    public boolean groupExists(String group)
    {
        return false;
    }

    @Override
    public Optional<String> getValue(String group, String key)
    {
        return Optional.empty();
    }

    @Override
    public boolean setValue(String group, String key, String value)
    {
        throw new UnsupportedOperationException("Storage is disabled");
    }

    @Override
    public void deleteValue(String group, String key)
    {
        // NOP
    }

    @Override
    public Optional<String> computeValue(String group, String key, UnaryOperator<Optional<String>> updater)
    {
        return Optional.empty();
    }

    @Override
    public void waitForSignal(String group, Duration timeout)
    {
        // NOP
    }

    @Override
    public void signalAll(String group)
    {
        // NOP
    }

    @Override
    public Stream<String> groups()
    {
        return Stream.empty();
    }

    @Override
    public Stream<String> keys(String group)
    {
        return Stream.empty();
    }
}
