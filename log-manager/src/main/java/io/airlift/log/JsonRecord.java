package io.airlift.log;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Throwables;

import java.time.Instant;
import java.util.Objects;

import static com.google.common.base.MoreObjects.toStringHelper;
import static java.util.Objects.requireNonNull;

public class JsonRecord
{
    private final Instant timestamp;
    private final Level level;
    private final String thread;
    private final String loggerName;
    private final String message;
    private final Throwable throwable;

    @JsonCreator
    public JsonRecord(
            @JsonProperty("timestamp") Instant timestamp,
            @JsonProperty("level") Level level,
            @JsonProperty("thread") String thread,
            @JsonProperty("logger") String loggerName,
            @JsonProperty("message") String message,
            Throwable throwable)
    {
        this.timestamp = requireNonNull(timestamp);
        this.level = requireNonNull(level);
        this.thread = thread;
        this.loggerName = loggerName;
        this.message = message;
        this.throwable = throwable;
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
