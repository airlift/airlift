package io.airlift.jfr;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Binder;
import com.google.inject.TypeLiteral;
import jdk.jfr.Period;
import jdk.jfr.StackTrace;
import jdk.jfr.Threshold;
import jdk.jfr.consumer.RecordedEvent;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import static com.google.inject.multibindings.MapBinder.newMapBinder;
import static java.util.Objects.requireNonNull;

public class FlightRecorderBinder
{
    private final Binder binder;
    private final String eventName;
    private final Map<String, String> properties = new HashMap<>();

    private FlightRecorderBinder(Binder binder, String eventName)
    {
        this.binder = requireNonNull(binder, "binder is null");
        this.eventName = requireNonNull(eventName, "eventName is null");
    }

    public static FlightRecorderBinder flightRecorderBinder(Binder binder, String eventName)
    {
        return new FlightRecorderBinder(binder, eventName);
    }

    public FlightRecorderBinder withThreshold(Duration threshold)
    {
        if (threshold == null) {
            properties.put(Threshold.NAME, "0 ns");
        }
        else {
            properties.put(Threshold.NAME, threshold.toNanos() + " ns");
        }
        return this;
    }

    public FlightRecorderBinder withStackTrace()
    {
        properties.put(StackTrace.NAME, "true");
        return this;
    }

    public FlightRecorderBinder withoutStackTrace()
    {
        properties.put(StackTrace.NAME, "false");
        return this;
    }

    public FlightRecorderBinder withPeriod(Duration period)
    {
        if (period == null) {
            properties.put(Period.NAME, "0 ns");
        }
        else {
            properties.put(Period.NAME, period.toNanos() + " ns");
        }
        return this;
    }

    public void bindTo(Class<? extends Consumer<RecordedEvent>> clazz)
    {
        newMapBinder(binder, new TypeLiteral<FlightRecorderEvent>(){}, new TypeLiteral<Consumer<RecordedEvent>>() {})
                .permitDuplicates()
                .addBinding(new FlightRecorderEvent(eventName, ImmutableMap.copyOf(properties)))
                .to(clazz);
    }

    public void bindTo(Consumer<RecordedEvent> clazz)
    {
        newMapBinder(binder, new TypeLiteral<FlightRecorderEvent>(){}, new TypeLiteral<Consumer<RecordedEvent>>() {})
                .permitDuplicates()
                .addBinding(new FlightRecorderEvent(eventName, ImmutableMap.copyOf(properties)))
                .toInstance(clazz);
    }
}
