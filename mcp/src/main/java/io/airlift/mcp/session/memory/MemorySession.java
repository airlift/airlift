package io.airlift.mcp.session.memory;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.ImmutableList;
import io.airlift.mcp.model.Event;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static com.google.common.collect.ImmutableList.toImmutableList;

class MemorySession
{
    private final AtomicLong nextSessionId = new AtomicLong();
    private final Cache<Long, String> events;
    private final Semaphore eventLatch = new Semaphore(0);
    private final Map<String, Object> values = new ConcurrentHashMap<>();

    MemorySession(Duration maxEventRetention)
    {
        events = CacheBuilder.newBuilder()
                .expireAfterWrite(maxEventRetention)
                .build();
    }

    void setValue(String key, Object value)
    {
        values.put(key, value);
    }

    Optional<Object> value(String key)
    {
        return Optional.ofNullable(values.get(key));
    }

    void deleteValue(String key)
    {
        values.remove(key);
    }

    void addEvent(String eventData)
    {
        long eventId = nextSessionId.getAndIncrement();
        events.put(eventId, eventData);
        eventLatch.release();
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    List<Event> pollEvents(long lastEventId, Duration timeout)
    {
        try {
            eventLatch.tryAcquire(timeout.toMillis(), TimeUnit.MILLISECONDS);
            eventLatch.drainPermits();

            return events.asMap().entrySet().stream()
                    .filter(entry -> entry.getKey() > lastEventId)
                    .map(entry -> new Event(entry.getKey(), entry.getValue()))
                    .collect(toImmutableList());
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        return ImmutableList.of();
    }
}
