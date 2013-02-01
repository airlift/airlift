package io.airlift.http.client;

import io.airlift.units.Duration;
import org.apache.http.HttpResponse;
import org.apache.http.conn.ConnectionKeepAliveStrategy;
import org.apache.http.protocol.HttpContext;

class FixedIntervalKeepAliveStrategy
        implements ConnectionKeepAliveStrategy
{
    private final long keepAliveInMs;

    public FixedIntervalKeepAliveStrategy(Duration keepAliveInterval)
    {
        if (keepAliveInterval == null) {
            keepAliveInMs = -1;
        } else {
            keepAliveInMs = (long) keepAliveInterval.toMillis();
        }
    }

    @Override
    public long getKeepAliveDuration(HttpResponse response, HttpContext context)
    {
        return keepAliveInMs;
    }
}
