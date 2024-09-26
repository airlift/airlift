package io.airlift.http.server;

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
        DoubleSummaryStats stats = event.responseContentInterarrivalStats();
        if (stats != null) {
            contentInterarrivalStats = format("%.2f, %.2f, %.2f, %d", stats.getMin(), stats.getAverage(), stats.getMax(), stats.getCount());
        }

        builder.append(ISO_FORMATTER.format(event.timeStamp()))
                .append('\t')
                .append(event.clientAddress())
                .append('\t')
                .append(event.method())
                .append('\t')
                .append(event.requestUri()) // TODO: escape
                .append('\t')
                .append(event.user())
                .append('\t')
                .append(event.agent()) // TODO: escape
                .append('\t')
                .append(event.responseCode())
                .append('\t')
                .append(event.requestSize())
                .append('\t')
                .append(event.responseSize())
                .append('\t')
                .append(event.timeToLastByte())
                .append('\t')
                .append(event.protocolVersion())
                .append('\t')
                .append(event.timeToDispatch())
                .append('\t')
                .append(event.timeToCompletion())
                .append('\t')
                .append(event.timeFromFirstToLastContent())
                .append('\t')
                .append(contentInterarrivalStats)
                .append('\n');

        return builder.toString();
    }
}
