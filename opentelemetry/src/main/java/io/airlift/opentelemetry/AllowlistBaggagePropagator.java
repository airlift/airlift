package io.airlift.opentelemetry;

import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.baggage.BaggageBuilder;
import io.opentelemetry.api.baggage.propagation.W3CBaggagePropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.context.propagation.TextMapSetter;

import java.util.Collection;
import java.util.Set;

/**
 * Wraps {@link W3CBaggagePropagator} to enforce a fail-closed allowlist on baggage keys, both when
 * baggage is injected into an outgoing carrier (e.g. HTTP headers) and when it is extracted from an
 * incoming one.
 */
final class AllowlistBaggagePropagator
        implements TextMapPropagator
{
    private final TextMapPropagator delegate = W3CBaggagePropagator.getInstance();
    private final Set<String> allowedKeys;
    private final int maxValueLength;

    public AllowlistBaggagePropagator(BaggageConfig config)
    {
        this.allowedKeys = config.getAllowedKeys();
        this.maxValueLength = config.getMaxValueLength();
    }

    @Override
    public Collection<String> fields()
    {
        return delegate.fields();
    }

    @Override
    public <C> void inject(Context context, C carrier, TextMapSetter<C> setter)
    {
        Baggage sanitized = sanitize(Baggage.fromContext(context));
        delegate.inject(sanitized.storeInContext(context), carrier, setter);
    }

    @Override
    public <C> Context extract(Context context, C carrier, TextMapGetter<C> getter)
    {
        Context extracted = delegate.extract(context, carrier, getter);
        Baggage sanitized = sanitize(Baggage.fromContext(extracted));
        return sanitized.storeInContext(extracted);
    }

    private Baggage sanitize(Baggage baggage)
    {
        BaggageBuilder builder = Baggage.builder();
        baggage.forEach((key, entry) -> {
            if (allowedKeys.contains(key) && isSafeValue(entry.getValue())) {
                builder.put(key, truncate(entry.getValue()));
            }
        });
        return builder.build();
    }

    private static boolean isSafeValue(String value)
    {
        return value.chars().noneMatch(Character::isISOControl);
    }

    private String truncate(String value)
    {
        return value.length() > maxValueLength ? value.substring(0, maxValueLength) : value;
    }
}
