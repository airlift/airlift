package io.airlift.mcp.handler;

import java.util.Optional;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.stream.Stream;

public class Handlers<T>
{
    private final ConcurrentNavigableMap<String, T> entries = new ConcurrentSkipListMap<>();

    public void add(String name, T entry)
    {
        if (entries.put(name, entry) != null) {
            throw new IllegalArgumentException("Entry with name \"%s\" already exists".formatted(name));
        }
    }

    public boolean remove(String name)
    {
        return (entries.remove(name) != null);
    }

    public Optional<T> entry(String name)
    {
        return Optional.ofNullable(entries.get(name));
    }

    public int size()
    {
        return entries.size();
    }

    public boolean isEmpty()
    {
        return entries.isEmpty();
    }

    public Stream<T> entries()
    {
        return entries.values().stream();
    }

    public Stream<T> entriesAfter(String name)
    {
        return entries.tailMap(name, false).values().stream();
    }
}
