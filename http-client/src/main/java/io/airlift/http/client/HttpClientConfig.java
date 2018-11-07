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
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.net.HostAndPort;
import io.airlift.configuration.Config;
import io.airlift.configuration.ConfigDescription;
import io.airlift.configuration.LegacyConfig;
import io.airlift.units.DataSize;
import io.airlift.units.Duration;
import io.airlift.units.MaxDataSize;
import io.airlift.units.MinDataSize;
import io.airlift.units.MinDuration;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

import java.util.List;

import static io.airlift.units.DataSize.Unit.GIGABYTE;
import static io.airlift.units.DataSize.Unit.KILOBYTE;
import static io.airlift.units.DataSize.Unit.MEGABYTE;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;

@Beta
public class HttpClientConfig
{
    public static final String JAVAX_NET_SSL_KEY_STORE = "javax.net.ssl.keyStore";
    public static final String JAVAX_NET_SSL_KEY_STORE_PASSWORD = "javax.net.ssl.keyStorePassword";
    public static final String JAVAX_NET_SSL_TRUST_STORE = "javax.net.ssl.trustStore";
    public static final String JAVAX_NET_SSL_TRUST_STORE_PASSWORD = "javax.net.ssl.trustStorePassword";

    private Duration connectTimeout = new Duration(5, SECONDS);
    private Duration requestTimeout = new Duration(5, MINUTES);
    private Duration idleTimeout = new Duration(1, MINUTES);
    private Duration keepAliveInterval;
    private int maxConnections = 200;
    private int maxConnectionsPerServer = 20;
    private int maxRequestsQueuedPerDestination = 1024;
    private DataSize maxContentLength = new DataSize(16, MEGABYTE);
    private DataSize requestBufferSize = new DataSize(4, KILOBYTE);
    private DataSize responseBufferSize = new DataSize(16, KILOBYTE);
    private HostAndPort socksProxy;
    private String keyStorePath = System.getProperty(JAVAX_NET_SSL_KEY_STORE);
    private String keyStorePassword = System.getProperty(JAVAX_NET_SSL_KEY_STORE_PASSWORD);
    private String trustStorePath = System.getProperty(JAVAX_NET_SSL_TRUST_STORE);
    private String trustStorePassword = System.getProperty(JAVAX_NET_SSL_TRUST_STORE_PASSWORD);
    private String secureRandomAlgorithm;
    private List<String> includedCipherSuites = ImmutableList.of();

    /**
     * This property is initialized with Jetty's default excluded ciphers list.
     *
     * @see org.eclipse.jetty.util.ssl.SslContextFactory#SslContextFactory(boolean, String)
     */
    private List<String> excludedCipherSuites = ImmutableList.of("^.*_(MD5|SHA|SHA1)$", "^TLS_RSA_.*$", "^SSL_.*$", "^.*_NULL_.*$", "^.*_anon_.*$");

    private boolean authenticationEnabled;
    private String kerberosPrincipal;
    private String kerberosRemoteServiceName;
    private int selectorCount = 2;
    private boolean recordRequestComplete = true;
    private boolean connectBlocking;

    private int maxThreads = 200;
    private int minThreads = 8;
    private int timeoutThreads = 1;
    private int timeoutConcurrency = 1;

    private boolean http2Enabled;
    private DataSize http2InitialSessionReceiveWindowSize = new DataSize(16, MEGABYTE);
    private DataSize http2InitialStreamReceiveWindowSize = new DataSize(16, MEGABYTE);
    private DataSize http2InputBufferSize = new DataSize(8, KILOBYTE);

    private String logPath = "var/log/";
    private boolean logEnabled;
    private int logHistory = 15;
    private int logQueueSize = 10_000;
    private DataSize logMaxFileSize = new DataSize(1, GIGABYTE);
    private DataSize logBufferSize = new DataSize(1, MEGABYTE);
    private Duration logFlushInterval = new Duration(10, SECONDS);
    private boolean logCompressionEnabled = true;

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

    @NotNull
    @MaxDataSize("32MB")
    public DataSize getRequestBufferSize()
    {
        return requestBufferSize;
    }

    @Config("http-client.request-buffer-size")
    public HttpClientConfig setRequestBufferSize(DataSize requestBufferSize)
    {
        this.requestBufferSize = requestBufferSize;
        return this;
    }

    @NotNull
    @MaxDataSize("32MB")
    public DataSize getResponseBufferSize()
    {
        return responseBufferSize;
    }

    @Config("http-client.response-buffer-size")
    public HttpClientConfig setResponseBufferSize(DataSize responseBufferSize)
    {
        this.responseBufferSize = responseBufferSize;
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

    public List<String> getHttpsIncludedCipherSuites()
    {
        return includedCipherSuites;
    }

    @Config("http-client.https.included-cipher")
    public HttpClientConfig setHttpsIncludedCipherSuites(String includedCipherSuites)
    {
        this.includedCipherSuites = Splitter
                .on(',')
                .trimResults()
                .omitEmptyStrings()
                .splitToList(requireNonNull(includedCipherSuites, "includedCipherSuites is null"));
        return this;
    }

    public List<String> getHttpsExcludedCipherSuites()
    {
        return excludedCipherSuites;
    }

    @Config("http-client.https.excluded-cipher")
    @ConfigDescription("Setting this config property overwrites Jetty's default excluded cipher suites")
    public HttpClientConfig setHttpsExcludedCipherSuites(String excludedCipherSuites)
    {
        this.excludedCipherSuites = Splitter
                .on(',')
                .trimResults()
                .omitEmptyStrings()
                .splitToList(requireNonNull(excludedCipherSuites, "excludedCipherSuites is null"));
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

    @NotNull
    @MinDataSize("1kB")
    @MaxDataSize("1GB")
    public DataSize getHttp2InitialSessionReceiveWindowSize()
    {
        return http2InitialSessionReceiveWindowSize;
    }

    @Config("http-client.http2.session-receive-window-size")
    @ConfigDescription("Initial size of session's flow control receive window for HTTP/2")
    public HttpClientConfig setHttp2InitialSessionReceiveWindowSize(DataSize http2InitialSessionReceiveWindowSize)
    {
        this.http2InitialSessionReceiveWindowSize = http2InitialSessionReceiveWindowSize;
        return this;
    }

    @NotNull
    @MinDataSize("1kB")
    @MaxDataSize("1GB")
    public DataSize getHttp2InitialStreamReceiveWindowSize()
    {
        return http2InitialStreamReceiveWindowSize;
    }

    @Config("http-client.http2.stream-receive-window-size")
    @ConfigDescription("Initial size of stream's flow control receive window for HTTP/2")
    public HttpClientConfig setHttp2InitialStreamReceiveWindowSize(DataSize http2InitialStreamReceiveWindowSize)
    {
        this.http2InitialStreamReceiveWindowSize = http2InitialStreamReceiveWindowSize;
        return this;
    }

    @NotNull
    @MinDataSize("1kB")
    @MaxDataSize("32MB")
    public DataSize getHttp2InputBufferSize()
    {
        return http2InputBufferSize;
    }

    @Config("http-client.http2.input-buffer-size")
    @ConfigDescription("Size of the buffer used to read from the network for HTTP/2")
    public HttpClientConfig setHttp2InputBufferSize(DataSize http2InputBufferSize)
    {
        this.http2InputBufferSize = http2InputBufferSize;
        return this;
    }

    @Min(1)
    public int getSelectorCount()
    {
        return selectorCount;
    }

    @Config("http-client.selector-count")
    public HttpClientConfig setSelectorCount(int selectorCount)
    {
        this.selectorCount = selectorCount;
        return this;
    }

    public boolean getRecordRequestComplete()
    {
        return recordRequestComplete;
    }

    @Config("http-client.record-request-complete")
    public HttpClientConfig setRecordRequestComplete(boolean recordRequestComplete)
    {
        this.recordRequestComplete = recordRequestComplete;
        return this;
    }

    public boolean isConnectBlocking()
    {
        return connectBlocking;
    }

    @Config("http-client.use-blocking-connect")
    public HttpClientConfig setConnectBlocking(boolean connectBlocking)
    {
        this.connectBlocking = connectBlocking;
        return this;
    }

    @Min(1)
    public int getMaxThreads()
    {
        return maxThreads;
    }

    @Config("http-client.max-threads")
    public HttpClientConfig setMaxThreads(int maxThreads)
    {
        this.maxThreads = maxThreads;
        return this;
    }

    @Min(1)
    public int getMinThreads()
    {
        return minThreads;
    }

    @Config("http-client.min-threads")
    public HttpClientConfig setMinThreads(int minThreads)
    {
        this.minThreads = minThreads;
        return this;
    }

    @Min(1)
    public int getTimeoutThreads()
    {
        return timeoutThreads;
    }

    @Config("http-client.timeout-threads")
    @ConfigDescription("Total number of timeout threads")
    public HttpClientConfig setTimeoutThreads(int timeoutThreads)
    {
        this.timeoutThreads = timeoutThreads;
        return this;
    }

    @Min(1)
    public int getTimeoutConcurrency()
    {
        return timeoutConcurrency;
    }

    @Config("http-client.timeout-concurrency")
    @ConfigDescription("Number of concurrent locks for timeout")
    public HttpClientConfig setTimeoutConcurrency(int timeoutConcurrency)
    {
        this.timeoutConcurrency = timeoutConcurrency;
        return this;
    }

    public String getLogPath()
    {
        return logPath;
    }

    @Config("http-client.log.path")
    @ConfigDescription("The name of the log file will be prefixed with the name of the HTTP client (<client_name>-http-client.log)")
    public HttpClientConfig setLogPath(String logPath)
    {
        this.logPath = logPath;
        return this;
    }

    public boolean isLogEnabled()
    {
        return logEnabled;
    }

    @Config("http-client.log.enabled")
    public HttpClientConfig setLogEnabled(boolean logEnabled)
    {
        this.logEnabled = logEnabled;
        return this;
    }

    public DataSize getLogMaxFileSize()
    {
        return logMaxFileSize;
    }

    @Config("http-client.log.max-size")
    public HttpClientConfig setLogMaxFileSize(DataSize logMaxFileSize)
    {
        this.logMaxFileSize = logMaxFileSize;
        return this;
    }

    public int getLogHistory()
    {
        return logHistory;
    }

    @Config("http-client.log.max-history")
    public HttpClientConfig setLogHistory(int logHistory)
    {
        this.logHistory = logHistory;
        return this;
    }

    @Min(1)
    public int getLogQueueSize()
    {
        return logQueueSize;
    }

    @Config("http-client.log.queue-size")
    public HttpClientConfig setLogQueueSize(int logQueueSize)
    {
        this.logQueueSize = logQueueSize;
        return this;
    }

    @NotNull
    @MinDataSize("1MB")
    @MaxDataSize("1GB")
    public DataSize getLogBufferSize()
    {
        return logBufferSize;
    }

    @Config("http-client.log.buffer-size")
    public HttpClientConfig setLogBufferSize(DataSize logBufferSize)
    {
        this.logBufferSize = logBufferSize;
        return this;
    }

    @NotNull
    public Duration getLogFlushInterval()
    {
        return logFlushInterval;
    }

    @Config("http-client.log.flush-interval")
    public HttpClientConfig setLogFlushInterval(Duration logFlushInterval)
    {
        this.logFlushInterval = logFlushInterval;
        return this;
    }

    public boolean isLogCompressionEnabled()
    {
        return logCompressionEnabled;
    }

    @Config("http-client.log.compression.enabled")
    public HttpClientConfig setLogCompressionEnabled(boolean logCompressionEnabled)
    {
        this.logCompressionEnabled = logCompressionEnabled;
        return this;
    }
}
