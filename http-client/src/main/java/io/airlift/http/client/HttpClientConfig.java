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
import io.airlift.configuration.ConfigDescription;
import io.airlift.configuration.LegacyConfig;
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
    public static final String JAVAX_NET_SSL_TRUST_STORE = "javax.net.ssl.trustStore";
    public static final String JAVAX_NET_SSL_TRUST_STORE_PASSWORD = "javax.net.ssl.trustStorePassword";

    private boolean http2Enabled;
    private Duration connectTimeout = new Duration(1, TimeUnit.SECONDS);
    private Duration requestTimeout = new Duration(5, TimeUnit.MINUTES);
    private Duration idleTimeout = new Duration(1, TimeUnit.MINUTES);
    private Duration keepAliveInterval;
    private int maxConnections = 200;
    private int maxConnectionsPerServer = 20;
    private int maxRequestsQueuedPerDestination = 1024;
    private DataSize maxContentLength = new DataSize(16, Unit.MEGABYTE);
    private HostAndPort socksProxy;
    private String keyStorePath = System.getProperty(JAVAX_NET_SSL_KEY_STORE);
    private String keyStorePassword = System.getProperty(JAVAX_NET_SSL_KEY_STORE_PASSWORD);
    private String trustStorePath = System.getProperty(JAVAX_NET_SSL_TRUST_STORE);
    private String trustStorePassword = System.getProperty(JAVAX_NET_SSL_TRUST_STORE_PASSWORD);
    private String secureRandomAlgorithm;
    private boolean authenticationEnabled;
    private String kerberosPrincipal;
    private String kerberosRemoteServiceName;

    public boolean isHttp2Enabled()
    {
        return http2Enabled;
    }

    @Config("http-client.http2.enabled")
    @ConfigDescription("Enable the HTTP/2 transport")
    public HttpClientConfig setHttp2Enabled(boolean http2Enabled)
    {
        this.http2Enabled = http2Enabled;
        return this;
    }

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
    public Duration getRequestTimeout()
    {
        return requestTimeout;
    }

    @Config("http-client.request-timeout")
    public HttpClientConfig setRequestTimeout(Duration requestTimeout)
    {
        this.requestTimeout = requestTimeout;
        return this;
    }

    @NotNull
    @MinDuration("0ms")
    public Duration getIdleTimeout()
    {
        return idleTimeout;
    }

    @Config("http-client.idle-timeout")
    @LegacyConfig("http-client.read-timeout")
    public HttpClientConfig setIdleTimeout(Duration idleTimeout)
    {
        this.idleTimeout = idleTimeout;
        return this;
    }

    @Deprecated
    public Duration getKeepAliveInterval()
    {
        return keepAliveInterval;
    }

    @Deprecated
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

    @Min(1)
    public int getMaxRequestsQueuedPerDestination()
    {
        return maxRequestsQueuedPerDestination;
    }

    @Config("http-client.max-requests-queued-per-destination")
    public HttpClientConfig setMaxRequestsQueuedPerDestination(int maxRequestsQueuedPerDestination)
    {
        this.maxRequestsQueuedPerDestination = maxRequestsQueuedPerDestination;
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

    public String getTrustStorePath()
    {
        return trustStorePath;
    }

    @Config("http-client.trust-store-path")
    public HttpClientConfig setTrustStorePath(String trustStorePath)
    {
        this.trustStorePath = trustStorePath;
        return this;
    }

    public String getTrustStorePassword()
    {
        return trustStorePassword;
    }

    @Config("http-client.trust-store-password")
    public HttpClientConfig setTrustStorePassword(String trustStorePassword)
    {
        this.trustStorePassword = trustStorePassword;
        return this;
    }

    public String getSecureRandomAlgorithm()
    {
        return secureRandomAlgorithm;
    }

    @Config("http-client.secure-random-algorithm")
    public HttpClientConfig setSecureRandomAlgorithm(String secureRandomAlgorithm)
    {
        this.secureRandomAlgorithm = secureRandomAlgorithm;
        return this;
    }

    public boolean getAuthenticationEnabled()
    {
        return authenticationEnabled;
    }

    @Config("http-client.authentication.enabled")
    @ConfigDescription("Enable client authentication")
    public HttpClientConfig setAuthenticationEnabled(boolean enabled)
    {
        this.authenticationEnabled = enabled;
        return this;
    }

    public String getKerberosPrincipal()
    {
        return kerberosPrincipal;
    }

    @Config("http-client.authentication.krb5.principal")
    @ConfigDescription("Set kerberos client principal")
    public HttpClientConfig setKerberosPrincipal(String kerberosClientPrincipal)
    {
        this.kerberosPrincipal = kerberosClientPrincipal;
        return this;
    }

    public String getKerberosRemoteServiceName()
    {
        return kerberosRemoteServiceName;
    }

    @Config("http-client.authentication.krb5.remote-service-name")
    @ConfigDescription("Set kerberos service principal name")
    public HttpClientConfig setKerberosRemoteServiceName(String serviceName)
    {
        this.kerberosRemoteServiceName = serviceName;
        return this;
    }
}
