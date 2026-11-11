package io.airlift.opentelemetry;

import com.google.common.collect.ImmutableList;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.Tracer;

import java.util.List;

import static java.util.Objects.requireNonNull;

final class FilteringTracer
        implements Tracer
{
    private final Tracer delegate;
    private final List<SpanFilterRule> rules;

    FilteringTracer(Tracer delegate, List<SpanFilterRule> rules)
    {
        this.delegate = requireNonNull(delegate, "delegate is null");
        this.rules = ImmutableList.copyOf(rules);
    }

    @Override
    public SpanBuilder spanBuilder(String spanName)
    {
        for (SpanFilterRule rule : rules) {
            if (rule.matchesName(spanName)) {
                return new FilteringSpanBuilder(rule.children());
            }
        }
        return delegate.spanBuilder(spanName);
    }
}
