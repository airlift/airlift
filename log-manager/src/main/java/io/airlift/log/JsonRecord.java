package io.airlift.log;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Throwables;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.context.Context;
import jakarta.annotation.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import static com.google.common.base.MoreObjects.firstNonNull;
import static com.google.common.base.MoreObjects.toStringHelper;
import static java.util.Objects.requireNonNull;

public class JsonRecord
{
    private static final Object[] EMPTY_ARRAY = new Object[0];

    private final Instant timestamp;
    private final Level level;
    private final String thread;
    private final String loggerName;
    private final String message;
    private final List<String> parameters;
    private final Throwable throwable;
    private final Optional<SpanContext> spanContext;
    private final Map<String, String> logAnnotations;

    public JsonRecord(
            Instant timestamp,
            Level level,
            String thread,
            String loggerName,
            String message,
            Object[] parameters,
            Throwable throwable,
            Context context,
            Map<String, String> logAnnotations)
    {
        this(
                timestamp,
                level,
                thread,
                loggerName,
                message,
                Stream.of(firstNonNull(parameters, EMPTY_ARRAY))
                        .map(Object::toString)
                        .toList(),
                throwable,
                context,
                logAnnotations);
    }

    @JsonCreator
    public JsonRecord(
            @JsonProperty("timestamp") Instant timestamp,
            @JsonProperty("level") Level level,
            @JsonProperty("thread") String thread,
            @JsonProperty("logger") String loggerName,
            @JsonProperty("message") String message,
            @JsonProperty("parameters") List<String> parameters,
            Throwable throwable,
            Context context,
            Map<String, String> logAnnotations)
    {
        this.timestamp = requireNonNull(timestamp);
        this.level = requireNonNull(level);
        this.thread = thread;
        this.loggerName = loggerName;
        this.message = message;
        if (parameters != null && parameters.isEmpty()) {
            // Avoid outputting useless empty "parameters" empty array in JSON output
            parameters = null;
        }
        this.parameters = parameters;
        this.throwable = throwable;
        this.spanContext = Optional.ofNullable(context)
                .map(Span::fromContext)
                .map(Span::getSpanContext)
                .filter(SpanContext::isValid);
        this.logAnnotations = (logAnnotations == null || logAnnotations.isEmpty()) ? null : logAnnotations;
    }

    @JsonProperty
    public Instant getTimestamp()
    {
        return timestamp;
    }

    @JsonProperty
    public Level getLevel()
    {
        return level;
    }

    @JsonProperty
    public String getThread()
    {
        return thread;
    }

    @JsonProperty("logger")
    public String getLoggerName()
    {
        return loggerName;
    }

    @JsonProperty
    public String getMessage()
    {
        return message;
    }

    @Nullable
    @JsonProperty
    public List<String> getParameters()
    {
        return parameters;
    }

    @JsonProperty
    public String getThrowableClass()
    {
        if (throwable == null) {
            return null;
        }
        return throwable.getClass().getName();
    }

    @JsonProperty
    public String getThrowableMessage()
    {
        if (throwable == null) {
            return null;
        }
        return throwable.getMessage();
    }

    @JsonProperty
    public String getStackTrace()
    {
        if (throwable == null) {
            return null;
        }
        return Throwables.getStackTraceAsString(throwable);
    }

    @JsonProperty
    public Optional<String> getTraceId()
    {
        return spanContext.map(SpanContext::getTraceId);
    }

    @JsonProperty
    public Optional<String> getSpanId()
    {
        return spanContext.map(SpanContext::getSpanId);
    }

    @JsonProperty
    public Optional<String> getTraceFlags()
    {
        return spanContext.map(SpanContext::getTraceFlags).map(TraceFlags::asHex);
    }

    @JsonProperty("annotations")
    public Map<String, String> getLogAnnotations()
    {
        return logAnnotations;
    }

    @Override
    public String toString()
    {
        return toStringHelper(this)
                .add("timestamp", timestamp)
                .add("level", level)
                .add("thread", thread)
                .add("loggerName", loggerName)
                .add("message", message)
                .add("throwable", throwable)
                .toString();
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        JsonRecord that = (JsonRecord) o;
        return Objects.equals(timestamp, that.timestamp)
                && level == that.level
                && Objects.equals(thread, that.thread)
                && Objects.equals(loggerName, that.loggerName)
                && Objects.equals(message, that.message)
                && Objects.equals(throwable, that.throwable);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(timestamp, level, thread, loggerName, message, throwable);
    }
}
