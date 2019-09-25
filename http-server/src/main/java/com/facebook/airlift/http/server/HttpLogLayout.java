package com.facebook.airlift.http.server;

import ch.qos.logback.core.LayoutBase;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import static java.lang.String.format;
import static java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME;

public class HttpLogLayout
        extends LayoutBase<HttpRequestEvent>
{
    private static final DateTimeFormatter ISO_FORMATTER = ISO_OFFSET_DATE_TIME.withZone(ZoneId.systemDefault());

    @Override
    public String doLayout(HttpRequestEvent event)
    {
        StringBuilder builder = new StringBuilder();

        // format content interarrival time [ms] stats
        String contentInterarrivalStats = null;
        DoubleSummaryStats stats = event.getResponseContentInterarrivalStats();
        if (stats != null) {
            contentInterarrivalStats = format("%.2f, %.2f, %.2f, %d", stats.getMin(), stats.getAverage(), stats.getMax(), stats.getCount());
        }

        builder.append(ISO_FORMATTER.format(event.getTimeStamp()))
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
                .append('\t')
                .append(event.getProtocolVersion())
                .append('\t')
                .append(event.getBeginToDispatchMillis())
                .append('\t')
                .append(event.getBeginToEndMillis())
                .append('\t')
                .append(event.getFirstToLastContentTimeInMillis())
                .append('\t')
                .append(contentInterarrivalStats)
                .append('\n');

        return builder.toString();
    }
}
