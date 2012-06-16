package io.airlift.http.client;

import com.google.common.annotations.Beta;
import io.airlift.configuration.Config;
import io.airlift.units.Duration;
import io.airlift.units.MinDuration;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import java.util.concurrent.TimeUnit;

@Beta
public class HttpClientConfig
{
    private Duration connectTimeout = new Duration(1, TimeUnit.SECONDS);
    private Duration readTimeout = new Duration(1, TimeUnit.MINUTES);
    private int maxConnections = 200;
    private int maxConnectionsPerServer = 20;

    @NotNull
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

    @NotNull
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

    @Min(1)
    public int getMaxConnections()
    {
        return maxConnections;
    }

    @Config("http-client.max-connections")
    public HttpClientConfig setMaxConnections(int maxConnections)
    {
        this.maxConnections = maxConnections;
        return this;
    }

    @Min(1)
    public int getMaxConnectionsPerServer()
    {
        return maxConnectionsPerServer;
    }

    @Config("http-client.max-connections-per-server")
    public HttpClientConfig setMaxConnectionsPerServer(int maxConnectionsPerServer)
    {
        this.maxConnectionsPerServer = maxConnectionsPerServer;
        return this;
    }
}
