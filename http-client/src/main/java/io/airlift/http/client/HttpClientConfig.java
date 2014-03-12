/*
 * Copyright 2010 Proofpoint, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.airlift.http.client;

import com.google.common.annotations.Beta;
import com.google.common.net.HostAndPort;
import io.airlift.configuration.Config;
import io.airlift.units.DataSize;
import io.airlift.units.DataSize.Unit;
import io.airlift.units.Duration;
import io.airlift.units.MinDuration;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

import java.util.concurrent.TimeUnit;

@Beta
public class HttpClientConfig
{
    public static final String JAVAX_NET_SSL_KEY_STORE = "javax.net.ssl.keyStore";
    public static final String JAVAX_NET_SSL_KEY_STORE_PASSWORD = "javax.net.ssl.keyStorePassword";

    private Duration connectTimeout = new Duration(1, TimeUnit.SECONDS);
    private Duration readTimeout = new Duration(1, TimeUnit.MINUTES);
    private Duration keepAliveInterval = null;
    private int maxConnections = 200;
    private int maxConnectionsPerServer = 20;
    private DataSize maxContentLength = new DataSize(16, Unit.MEGABYTE);
    private HostAndPort socksProxy;
    private String keyStorePath = System.getProperty(JAVAX_NET_SSL_KEY_STORE);
    private String keyStorePassword = System.getProperty(JAVAX_NET_SSL_KEY_STORE_PASSWORD);

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

    public Duration getKeepAliveInterval()
    {
        return keepAliveInterval;
    }

    @Config("http-client.keep-alive-interval")
    public HttpClientConfig setKeepAliveInterval(Duration keepAliveInterval)
    {
        this.keepAliveInterval = keepAliveInterval;
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

    @NotNull
    public DataSize getMaxContentLength()
    {
        return maxContentLength;
    }

    @Config("http-client.max-content-length")
    public HttpClientConfig setMaxContentLength(DataSize maxContentLength)
    {
        this.maxContentLength = maxContentLength;
        return this;
    }

    public HostAndPort getSocksProxy()
    {
        return socksProxy;
    }

    @Config("http-client.socks-proxy")
    public HttpClientConfig setSocksProxy(HostAndPort socksProxy)
    {
        this.socksProxy = socksProxy;
        return this;
    }

    public String getKeyStorePath()
    {
        return keyStorePath;
    }

    @Config("http-client.key-store-path")
    public HttpClientConfig setKeyStorePath(String keyStorePath)
    {
        this.keyStorePath = keyStorePath;
        return this;
    }

    public String getKeyStorePassword()
    {
        return keyStorePassword;
    }

    @Config("http-client.key-store-password")
    public HttpClientConfig setKeyStorePassword(String keyStorePassword)
    {
        this.keyStorePassword = keyStorePassword;
        return this;
    }
}
