package io.airlift.opentelemetry;

import io.airlift.opentelemetry.SpanFilterRule.ChildrenMode;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.trace.IdGenerator;

import java.util.concurrent.TimeUnit;

import static io.airlift.opentelemetry.SpanFilterRule.ChildrenMode.REPARENT;
import static java.util.Objects.requireNonNull;

final class FilteringSpanBuilder
        implements SpanBuilder
{
    private static final IdGenerator ID_GENERATOR = IdGenerator.random();

    private final ChildrenMode children;
    private Context parent;
    private boolean noParent;

    FilteringSpanBuilder(ChildrenMode children)
    {
        this.children = requireNonNull(children, "children is null");
    }

    @Override
    public SpanBuilder setParent(Context context)
    {
        if (context != null) {
            parent = context;
            noParent = false;
        }
        return this;
    }

    @Override
    public SpanBuilder setNoParent()
    {
        parent = null;
        noParent = true;
        return this;
    }

    @Override
    public SpanBuilder addLink(SpanContext spanContext)
    {
        return this;
    }

    @Override
    public SpanBuilder addLink(SpanContext spanContext, Attributes attributes)
    {
        return this;
    }

    @Override
    public SpanBuilder setAttribute(String key, String value)
    {
        return this;
    }

    @Override
    public SpanBuilder setAttribute(String key, long value)
    {
        return this;
    }

    @Override
    public SpanBuilder setAttribute(String key, double value)
    {
        return this;
    }

    @Override
    public SpanBuilder setAttribute(String key, boolean value)
    {
        return this;
    }

    @Override
    public <T> SpanBuilder setAttribute(AttributeKey<T> key, T value)
    {
        return this;
    }

    @Override
    public SpanBuilder setSpanKind(SpanKind spanKind)
    {
        return this;
    }

    @Override
    public SpanBuilder setStartTimestamp(long startTimestamp, TimeUnit unit)
    {
        return this;
    }

    @Override
    public Span startSpan()
    {
        Span parentSpan = noParent
                ? Span.getInvalid()
                : Span.fromContext(parent == null ? Context.current() : parent);
        SpanContext parentContext = parentSpan.getSpanContext();

        if (children == REPARENT && parentContext.isValid()) {
            return new ReparentedSpan(parentSpan);
        }

        String traceId = parentContext.isValid() ? parentContext.getTraceId() : ID_GENERATOR.generateTraceId();
        return Span.wrap(SpanContext.create(traceId, ID_GENERATOR.generateSpanId(), TraceFlags.getDefault(), parentContext.getTraceState()));
    }

    private record ReparentedSpan(Span parent)
            implements Span
    {
        private ReparentedSpan
        {
            requireNonNull(parent, "parent is null");
        }

        @Override
        public <T> Span setAttribute(AttributeKey<T> key, T value)
        {
            return this;
        }

        @Override
        public Span addEvent(String name, Attributes attributes)
        {
            return this;
        }

        @Override
        public Span addEvent(String name, Attributes attributes, long timestamp, TimeUnit unit)
        {
            return this;
        }

        @Override
        public Span setStatus(StatusCode statusCode, String description)
        {
            return this;
        }

        @Override
        public Span recordException(Throwable exception, Attributes additionalAttributes)
        {
            return this;
        }

        @Override
        public Span updateName(String name)
        {
            return this;
        }

        @Override
        public void end() {}

        @Override
        public void end(long timestamp, TimeUnit unit) {}

        @Override
        public SpanContext getSpanContext()
        {
            return parent.getSpanContext();
        }

        @Override
        public boolean isRecording()
        {
            return parent.isRecording();
        }
    }
}
