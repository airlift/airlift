package io.airlift.log;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import io.airlift.json.JsonCodec;
import io.airlift.json.JsonCodecFactory;
import io.airlift.json.ObjectMapperProvider;
import io.opentelemetry.context.Context;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Map;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;

import static io.airlift.json.ObjectMapperProvider.toObjectMapperProvider;
import static java.util.Objects.requireNonNull;

public class JsonFormatter
        extends Formatter
{
    private static final Object[] EMPTY_ARRAY = new Object[0];
    private static final JsonCodec<JsonRecord> CODEC = new JsonCodecFactory(toObjectMapperProvider(new ObjectMapperProvider()))
            .jsonCodec(JsonRecord.class);
    private static final JsonFactory jsonFactory = new JsonFactory();
    private final Map<String, String> logAnnotations;

    public JsonFormatter(Map<String, String> logAnnotations)
    {
        this.logAnnotations = ImmutableMap.copyOf(requireNonNull(logAnnotations, "logAnnotations is null"));
    }

    @Override
    public String format(LogRecord record)
    {
        JsonRecord jsonRecord = new JsonRecord(
                record.getInstant(),
                Level.fromJulLevel(record.getLevel()),
                Thread.currentThread().getName(),
                record.getLoggerName(),
                record.getMessage(),
                record.getParameters(),
                record.getThrown(),
                Context.current(),
                logAnnotations);

        try {
            return toString(jsonRecord);
        }
        catch (IllegalArgumentException outer) {
            try {
                return toString(new JsonRecord(
                        record.getInstant(),
                        Level.fromJulLevel(record.getLevel()),
                        Thread.currentThread().getName(),
                        record.getLoggerName(),
                        outer.getMessage(),
                        EMPTY_ARRAY,
                        outer,
                        Context.current(),
                        logAnnotations));
            }
            catch (IllegalArgumentException inner) {
                inner.addSuppressed(outer);

                return minimalJsonErrorLogLine(jsonRecord, inner);
            }
        }
    }

    /**
     * Creates a minimalistic log line using JsonGenerator and avoiding the codecs and object mapper so that at least we get a json parseable log line
     */
    @VisibleForTesting
    String minimalJsonErrorLogLine(JsonRecord jsonRecord, Exception exception)
    {
        // Emit a log line that is at least json parseable and indicates things are broken
        StringWriter stringWriter = new StringWriter();
        try (JsonGenerator jsonGenerator = jsonFactory.createGenerator(stringWriter)) {
            jsonGenerator.writeStartObject();
            jsonGenerator.writeStringField("timestamp", jsonRecord.getTimestamp().toString());
            jsonGenerator.writeStringField("message", exception.getMessage());
            jsonGenerator.writeStringField("level", Level.ERROR.name());
            jsonGenerator.writeEndObject();
        }
        catch (IOException e) {
            e.addSuppressed(exception);
            // We're using a StringWriter, so all of the operations should be in-memory, and there shouldn't be a way to throw an IOException, but just in case...
            throw new RuntimeException("Unable to generate json logs", e);
        }
        return stringWriter.append("\n").toString();
    }

    private static String toString(JsonRecord jsonRecord)
    {
        return CODEC.toJson(jsonRecord) + "\n";
    }
}
