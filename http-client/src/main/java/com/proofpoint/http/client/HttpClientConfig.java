package com.proofpoint.http.client;

import com.proofpoint.configuration.Config;
import com.proofpoint.units.Duration;
import com.proofpoint.units.MinDuration;

import javax.validation.constraints.Min;
import java.util.concurrent.TimeUnit;

public class HttpClientConfig
{
    private int workerThreads = 16;
    private Duration connectTimeout = new Duration(1, TimeUnit.SECONDS);
    private Duration readTimeout = new Duration(1, TimeUnit.MINUTES);

    @Min(1)
    public int getWorkerThreads()
    {
        return workerThreads;
    }

    @Config("http-client.threads")
    public void setWorkerThreads(int workerThreads)
    {
        this.workerThreads = workerThreads;
    }

    @MinDuration("0ms")
    public Duration getConnectTimeout()
    {
        return connectTimeout;
    }

    @Config("http-client.connect-timeout")
    public HttpClientConfig setConnectTimeout(Duration connectTimeout)
    {
        this.connectTimeout = connectTimeout;
        return this;
    }

    @MinDuration("0ms")
    public Duration getReadTimeout()
    {
        return readTimeout;
    }

    @Config("http-client.read-timeout")
    public HttpClientConfig setReadTimeout(Duration readTimeout)
    {
        this.readTimeout = readTimeout;
        return this;
    }
}
