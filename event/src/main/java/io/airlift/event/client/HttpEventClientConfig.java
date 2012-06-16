package com.proofpoint.event.client;

import com.google.common.base.Preconditions;
import com.proofpoint.configuration.Config;
import com.proofpoint.configuration.ConfigDescription;
import com.proofpoint.units.Duration;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

public class HttpEventClientConfig
{
    private int maxConnections = -1;
    private Duration connectTimeout = new Duration(50, MILLISECONDS);
    private Duration requestTimeout = new Duration(60, SECONDS);
    private boolean compress;
    private int jsonVersion = 2;

    public int getMaxConnections()
    {
        return maxConnections;
    }

    @Config("collector.max-connections")
    @ConfigDescription("Get max number of simultaneous connections")
    public HttpEventClientConfig setMaxConnections(int maxConnections)
    {
        this.maxConnections = maxConnections;
        return this;
    }

    public Duration getConnectTimeout()
    {
        return connectTimeout;
    }

    @Config("collector.connect-timeout")
    public HttpEventClientConfig setConnectTimeout(Duration connectTimeout)
    {
        Preconditions.checkNotNull(connectTimeout, "connectionTimeout");
        if (connectTimeout.toMillis() < 1) {
            throw new IllegalArgumentException("Connection timeout must be greater than 0");
        }
        this.connectTimeout = connectTimeout;
        return this;
    }

    public Duration getRequestTimeout()
    {
        return requestTimeout;
    }

    @Config("collector.request-timeout")
    public HttpEventClientConfig setRequestTimeout(Duration requestTimeout)
    {
        Preconditions.checkNotNull(requestTimeout, "requestTimeout");
        if (requestTimeout.toMillis() < 1) {
            throw new IllegalArgumentException("Request timeout must be greater than 0");
        }
        this.requestTimeout = requestTimeout;
        return this;
    }

    public boolean isCompress()
    {
        return compress;
    }

    @Config("collector.compress")
    @ConfigDescription("If true, gzip compress data posted")
    public HttpEventClientConfig setCompress(boolean compress)
    {
        this.compress = compress;
        return this;
    }

    @Deprecated
    @Min(1)
    @Max(2)
    public int getJsonVersion()
    {
        return jsonVersion;
    }

    @Deprecated
    @Config("collector.json-version")
    @ConfigDescription("JSON format version supported by collector")
    public HttpEventClientConfig setJsonVersion(int jsonVersion)
    {
        this.jsonVersion = jsonVersion;
        return this;
    }
}
