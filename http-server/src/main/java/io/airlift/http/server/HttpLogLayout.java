package io.airlift.http.server;

import ch.qos.logback.core.LayoutBase;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.DateTimeFormatterBuilder;
import org.joda.time.format.ISODateTimeFormat;

public class HttpLogLayout
        extends LayoutBase<HttpRequestEvent>
{
    private static final DateTimeFormatter ISO_FORMATTER = new DateTimeFormatterBuilder()
            .append(ISODateTimeFormat.dateHourMinuteSecondFraction())
            .appendTimeZoneOffset("Z", true, 2, 2)
            .toFormatter();

    public String doLayout(HttpRequestEvent event)
    {
        StringBuilder builder = new StringBuilder();
        builder.append(ISO_FORMATTER.print(event.getTimeStamp()))
                .append('\t')
                .append(event.getClientAddress())
                .append('\t')
                .append(event.getMethod())
                .append('\t')
                .append(event.getRequestUri()) // TODO: escape
                .append('\t')
                .append(event.getUser())
                .append('\t')
                .append(event.getAgent()) // TODO: escape
                .append('\t')
                .append(event.getResponseCode())
                .append('\t')
                .append(event.getRequestSize())
                .append('\t')
                .append(event.getResponseSize())
                .append('\t')
                .append(event.getTimeToLastByte())
                .append('\t')
                .append(event.getTraceToken())
                .append('\n');

        String line = builder.toString();
        return line;
    }
}
