package io.airlift.http.client.jetty;

import ch.qos.logback.core.LayoutBase;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import static java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME;

class HttpClientLogLayout
        extends LayoutBase<HttpRequestEvent>
{
    private static final DateTimeFormatter ISO_FORMATTER = ISO_OFFSET_DATE_TIME.withZone(ZoneId.systemDefault());

    @Override
    public String doLayout(HttpRequestEvent event)
    {
        StringBuilder builder = new StringBuilder();
        builder.append(ISO_FORMATTER.format(event.getTimeStamp()))
                .append('\t')
                .append(event.getProtocolVersion())
                .append('\t')
                .append(event.getMethod())
                .append('\t')
                .append(event.getRequestUri()) // TODO: escape
                .append('\t')
                .append(event.getResponseCode())
                .append('\t')
                .append(event.getResponseSize())
                .append('\t')
                .append(event.getTimeToLastByte())
                .append('\t')
                .append(event.getTraceToken())
                .append('\n');
        return builder.toString();
    }
}
