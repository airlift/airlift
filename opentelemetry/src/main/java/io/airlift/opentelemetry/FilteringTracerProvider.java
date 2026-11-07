package io.airlift.opentelemetry;

import com.google.common.collect.ImmutableList;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.TracerBuilder;
import io.opentelemetry.api.trace.TracerProvider;

import java.util.List;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.util.Objects.requireNonNull;

public final class FilteringTracerProvider
        implements TracerProvider
{
    private final TracerProvider delegate;
    private final List<SpanFilterRule> rules;

    public FilteringTracerProvider(TracerProvider delegate, List<SpanFilterRule> rules)
    {
        this.delegate = requireNonNull(delegate, "delegate is null");
        this.rules = ImmutableList.copyOf(rules);
    }

    @Override
    public Tracer get(String instrumentationScopeName)
    {
        return filter(instrumentationScopeName, delegate.get(instrumentationScopeName));
    }

    @Override
    public Tracer get(String instrumentationScopeName, String instrumentationScopeVersion)
    {
        return filter(instrumentationScopeName, delegate.get(instrumentationScopeName, instrumentationScopeVersion));
    }

    @Override
    public TracerBuilder tracerBuilder(String instrumentationScopeName)
    {
        return new FilteringTracerBuilder(instrumentationScopeName, delegate.tracerBuilder(instrumentationScopeName));
    }

    private Tracer filter(String instrumentationScopeName, Tracer tracer)
    {
        List<SpanFilterRule> scopeRules = rules.stream()
                .filter(rule -> rule.matchesScope(instrumentationScopeName))
                .collect(toImmutableList());
        if (scopeRules.isEmpty()) {
            return tracer;
        }
        return new FilteringTracer(tracer, scopeRules);
    }

    private final class FilteringTracerBuilder
            implements TracerBuilder
    {
        private final String instrumentationScopeName;
        private final TracerBuilder delegate;

        private FilteringTracerBuilder(String instrumentationScopeName, TracerBuilder delegate)
        {
            this.instrumentationScopeName = requireNonNull(instrumentationScopeName, "instrumentationScopeName is null");
            this.delegate = requireNonNull(delegate, "delegate is null");
        }

        @Override
        public TracerBuilder setSchemaUrl(String schemaUrl)
        {
            delegate.setSchemaUrl(schemaUrl);
            return this;
        }

        @Override
        public TracerBuilder setInstrumentationVersion(String instrumentationScopeVersion)
        {
            delegate.setInstrumentationVersion(instrumentationScopeVersion);
            return this;
        }

        @Override
        public Tracer build()
        {
            return filter(instrumentationScopeName, delegate.build());
        }
    }
}
