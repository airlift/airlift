package io.airlift.log;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.SignStyle;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;

import static io.airlift.log.TerminalColors.Color.BRIGHT_BLACK;
import static io.airlift.log.TerminalColors.Color.CYAN;
import static io.airlift.log.TerminalColors.Color.GREEN;
import static io.airlift.log.TerminalColors.Color.PURPLE;
import static io.airlift.log.TerminalColors.Color.WHITE;
import static io.airlift.log.TerminalColors.coloredWriter;
import static java.time.temporal.ChronoField.DAY_OF_MONTH;
import static java.time.temporal.ChronoField.HOUR_OF_DAY;
import static java.time.temporal.ChronoField.MILLI_OF_SECOND;
import static java.time.temporal.ChronoField.MINUTE_OF_HOUR;
import static java.time.temporal.ChronoField.MONTH_OF_YEAR;
import static java.time.temporal.ChronoField.SECOND_OF_MINUTE;
import static java.time.temporal.ChronoField.YEAR;
import static java.util.Arrays.deepToString;
import static java.util.Objects.requireNonNull;

class StaticFormatter
        extends Formatter
{
    private static final ZoneId SYSTEM_ZONE = ZoneId.systemDefault().normalized();
    private final Map<String, String> logAnnotations;
    private final TerminalColors colors;

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

    public StaticFormatter()
    {
        this(ImmutableMap.of(), false);
    }

    public StaticFormatter(Map<String, String> logAnnotations, boolean interactive)
    {
        this.logAnnotations = ImmutableMap.copyOf(requireNonNull(logAnnotations, "logAnnotations is null"));
        this.colors = new TerminalColors(interactive);
    }

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
        Level level = Level.fromJulLevel(record.getLevel());

        StringWriter stringWriter = new StringWriter()
                .append(colors.colored(TIMESTAMP_FORMATTER.format(timestamp), BRIGHT_BLACK))
                .append('\t')
                .append(colors.colored(level.name(), level))
                .append('\t')
                .append(colors.colored(Thread.currentThread().getName(), CYAN))
                .append('\t')
                .append(colors.colored(record.getLoggerName(), PURPLE));

        if (!logAnnotations.isEmpty()) {
            stringWriter.append('\t')
                    .append(colors.colored(Joiner.on(",").withKeyValueSeparator("=").join(logAnnotations), level));
        }

        stringWriter.append('\t')
                .append(colors.colored(record.getMessage(), WHITE));

        if (record.getParameters() != null && record.getParameters().length != 0) {
            stringWriter.append(" parameters=").append(deepToString(record.getParameters()));
        }

        if (record.getThrown() != null) {
            stringWriter.append('\n');
            record.getThrown().printStackTrace(coloredWriter(new PrintWriter(stringWriter), GREEN));
            stringWriter.append('\n');
        }

        stringWriter.append('\n');
        return stringWriter.toString();
    }
}
