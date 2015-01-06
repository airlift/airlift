package io.airlift.log;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.SignStyle;
import java.util.Locale;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;

import static java.time.temporal.ChronoField.DAY_OF_MONTH;
import static java.time.temporal.ChronoField.HOUR_OF_DAY;
import static java.time.temporal.ChronoField.MILLI_OF_SECOND;
import static java.time.temporal.ChronoField.MINUTE_OF_HOUR;
import static java.time.temporal.ChronoField.MONTH_OF_YEAR;
import static java.time.temporal.ChronoField.SECOND_OF_MINUTE;
import static java.time.temporal.ChronoField.YEAR;

class StaticFormatter
        extends Formatter
{
    private static final ZoneId SYSTEM_ZONE = ZoneId.systemDefault().normalized();

    private static final DateTimeFormatter TIMESTAMP_FORMATTER = new DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .appendValue(YEAR, 4, 10, SignStyle.EXCEEDS_PAD)
            .appendLiteral('-')
            .appendValue(MONTH_OF_YEAR, 2)
            .appendLiteral('-')
            .appendValue(DAY_OF_MONTH, 2)
            .appendLiteral('T')
            .appendValue(HOUR_OF_DAY, 2)
            .appendLiteral(':')
            .appendValue(MINUTE_OF_HOUR, 2)
            .appendLiteral(':')
            .appendValue(SECOND_OF_MINUTE, 2)
            .appendLiteral('.')
            .appendValue(MILLI_OF_SECOND, 3)
            .appendOffset("+HHMM", "Z")
            .toFormatter(Locale.US);

    @Override
    @SuppressWarnings("NonSynchronizedMethodOverridesSynchronizedMethod")
    public String formatMessage(LogRecord record)
    {
        return format(record);
    }

    @Override
    public String format(LogRecord record)
    {
        ZonedDateTime timestamp = ZonedDateTime.ofInstant(Instant.ofEpochMilli(record.getMillis()), SYSTEM_ZONE);

        StringWriter stringWriter = new StringWriter()
                .append(TIMESTAMP_FORMATTER.format(timestamp))
                .append('\t')
                .append(Level.fromJulLevel(record.getLevel()).name())
                .append('\t')
                .append(Thread.currentThread().getName())
                .append('\t')
                .append(record.getLoggerName())
                .append('\t')
                .append(record.getMessage());

        if (record.getThrown() != null) {
            stringWriter.append('\n');
            record.getThrown().printStackTrace(new PrintWriter(stringWriter));
            stringWriter.append('\n');
        }

        stringWriter.append('\n');
        return stringWriter.toString();
    }
}
