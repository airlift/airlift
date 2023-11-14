package io.airlift.tracing;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.TracerProvider;
import jakarta.annotation.Nullable;

import java.util.Optional;

public final class Tracing
{
    private Tracing() {}

    public static Tracer noopTracer()
    {
        return TracerProvider.noop().get("noop");
    }

    @Deprecated(forRemoval = true)
    public static <T> Attributes attribute(AttributeKey<T> key, Optional<T> value)
    {
        return value.map(x -> Attributes.of(key, x)).orElseGet(Attributes::empty);
    }

    @Deprecated(forRemoval = true)
    public static <T> Attributes attribute(AttributeKey<T> key, @Nullable T value)
    {
        return (value != null) ? Attributes.of(key, value) : Attributes.empty();
    }
}
