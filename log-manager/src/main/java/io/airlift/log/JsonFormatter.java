package io.airlift.log;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.google.common.annotations.VisibleForTesting;
import io.airlift.json.JsonCodec;
import io.airlift.json.JsonCodecFactory;
import io.airlift.json.ObjectMapperProvider;

import java.io.IOException;
import java.io.StringWriter;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;

public class JsonFormatter
        extends Formatter
{
    private static final JsonCodec<JsonRecord> CODEC = new JsonCodecFactory(new ObjectMapperProvider()).jsonCodec(JsonRecord.class);
    private static final JsonFactory jsonFactory = new JsonFactory();

    @Override
    public String format(LogRecord record)
    {
        JsonRecord jsonRecord = new JsonRecord(
                record.getInstant(),
                Level.fromJulLevel(record.getLevel()),
                Thread.currentThread().getName(),
                record.getLoggerName(),
                record.getMessage(),
                record.getThrown());

        try {
            return new StringWriter().append(CODEC.toJson(jsonRecord)).append("\n").toString();
        }
        catch (IllegalArgumentException outer) {
            try {
                return new StringWriter().append(CODEC.toJson(new JsonRecord(
                        record.getInstant(),
                        Level.fromJulLevel(record.getLevel()),
                        Thread.currentThread().getName(),
                        record.getLoggerName(),
                        outer.getMessage(),
                        outer))).append("\n").toString();
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
}
