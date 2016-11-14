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
package io.airlift.http.server;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import io.airlift.configuration.Config;
import io.airlift.configuration.ConfigDescription;
import io.airlift.configuration.DefunctConfig;
import io.airlift.units.DataSize;
import io.airlift.units.Duration;

import javax.validation.constraints.Min;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static java.util.Objects.requireNonNull;

@DefunctConfig({
        "jetty.http.enabled",
        "jetty.http.port",
        "jetty.https.enabled",
        "jetty.https.port",
        "jetty.https.keystore.path",
        "jetty.https.keystore.password",
        "jetty.log.path",
        "jetty.log.retain-days",
        "jetty.threads.min",
        "jetty.threads.max",
        "jetty.threads.max-idle-time-ms",
        "jetty.net.max-idle-time-ms",
        "jetty.auth.users-file",
        "http-server.https.keystore.password",
        "http-server.log.retention-time",
})
public class HttpServerConfig
{
    private boolean httpEnabled = true;
    private int httpPort = 8080;
    private int httpAcceptQueueSize = 8000;

    private boolean httpsEnabled = false;
    private int httpsPort = 8443;
    private String keystorePath;
    private String keystorePassword;
    private String secureRandomAlgorithm;
    private List<String> includedCipherSuites = ImmutableList.of();
    private List<String> excludedCipherSuites = ImmutableList.of();

    private String logPath = "var/log/http-request.log";
    private boolean logEnabled = true;
    private int logHistory = 15;
    private DataSize logMaxFileSize = new DataSize(Long.MAX_VALUE, DataSize.Unit.BYTE);

    private Integer httpAcceptorThreads;
    private Integer httpSelectorThreads;
    private Integer httpsAcceptorThreads;
    private Integer httpsSelectorThreads;

    private int minThreads = 2;
    private int maxThreads = 200;
    private Duration threadMaxIdleTime = new Duration(1, TimeUnit.MINUTES);
    private Duration networkMaxIdleTime = new Duration(200, TimeUnit.SECONDS);
    private DataSize maxRequestHeaderSize;
    private int http2MaxConcurrentStreams = 16384;

    private String userAuthFile;

    private boolean adminEnabled = true;
    private int adminPort = 0;
    private int adminMinThreads = 2;
    private int adminMaxThreads = 200;

    private boolean showStackTrace = true;

    public boolean isHttpEnabled()
    {
        return httpEnabled;
    }

    @Config("http-server.http.enabled")
    public HttpServerConfig setHttpEnabled(boolean httpEnabled)
    {
        this.httpEnabled = httpEnabled;
        return this;
    }

    public int getHttpPort()
    {
        return httpPort;
    }

    @Config("http-server.accept-queue-size")
    public HttpServerConfig setHttpAcceptQueueSize(int httpAcceptQueueSize)
    {
        this.httpAcceptQueueSize = httpAcceptQueueSize;
        return this;
    }

    public int getHttpAcceptQueueSize()
    {
        return httpAcceptQueueSize;
    }

    @Config("http-server.http.port")
    public HttpServerConfig setHttpPort(int httpPort)
    {
        this.httpPort = httpPort;
        return this;
    }

    public boolean isHttpsEnabled()
    {
        return httpsEnabled;
    }

    @Config("http-server.https.enabled")
    public HttpServerConfig setHttpsEnabled(boolean httpsEnabled)
    {
        this.httpsEnabled = httpsEnabled;
        return this;
    }

    public int getHttpsPort()
    {
        return httpsPort;
    }

    @Config("http-server.https.port")
    public HttpServerConfig setHttpsPort(int httpsPort)
    {
        this.httpsPort = httpsPort;
        return this;
    }

    public String getKeystorePath()
    {
        return keystorePath;
    }

    @Config("http-server.https.keystore.path")
    public HttpServerConfig setKeystorePath(String keystorePath)
    {
        this.keystorePath = keystorePath;
        return this;
    }

    public String getKeystorePassword()
    {
        return keystorePassword;
    }

    @Config("http-server.https.keystore.key")
    public HttpServerConfig setKeystorePassword(String keystorePassword)
    {
        this.keystorePassword = keystorePassword;
        return this;
    }

    public String getSecureRandomAlgorithm()
    {
        return secureRandomAlgorithm;
    }

    @Config("http-server.https.secure-random-algorithm")
    public HttpServerConfig setSecureRandomAlgorithm(String secureRandomAlgorithm)
    {
        this.secureRandomAlgorithm = secureRandomAlgorithm;
        return this;
    }

    public List<String> getHttpsIncludedCipherSuites()
    {
        return includedCipherSuites;
    }

    @Config("http-server.https.included-cipher")
    public HttpServerConfig setHttpsIncludedCipherSuites(String includedCipherSuites)
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

    @Config("http-server.https.excluded-cipher")
    public HttpServerConfig setHttpsExcludedCipherSuites(String excludedCipherSuites)
    {
        this.excludedCipherSuites = Splitter
                .on(',')
                .trimResults()
                .omitEmptyStrings()
                .splitToList(requireNonNull(excludedCipherSuites, "excludedCipherSuites is null"));
        return this;
    }

    public String getLogPath()
    {
        return logPath;
    }

    @Config("http-server.log.path")
    public HttpServerConfig setLogPath(String logPath)
    {
        this.logPath = logPath;
        return this;
    }

    public boolean isLogEnabled()
    {
        return logEnabled;
    }

    @Config("http-server.log.enabled")
    public HttpServerConfig setLogEnabled(boolean logEnabled)
    {
        this.logEnabled = logEnabled;
        return this;
    }

    public DataSize getLogMaxFileSize()
    {
        return logMaxFileSize;
    }

    @Config("http-server.log.max-size")
    public HttpServerConfig setLogMaxFileSize(DataSize logMaxFileSize)
    {
        this.logMaxFileSize = logMaxFileSize;
        return this;
    }

    @Min(1)
    public Integer getHttpAcceptorThreads()
    {
        return httpAcceptorThreads;
    }

    @Config("http-server.http.acceptor-threads")
    public HttpServerConfig setHttpAcceptorThreads(Integer httpAcceptorThreads)
    {
        this.httpAcceptorThreads = httpAcceptorThreads;
        return this;
    }

    @Min(1)
    public Integer getHttpSelectorThreads()
    {
        return httpSelectorThreads;
    }

    @Config("http-server.http.selector-threads")
    public HttpServerConfig setHttpSelectorThreads(Integer httpSelectorThreads)
    {
        this.httpSelectorThreads = httpSelectorThreads;
        return this;
    }

    @Min(1)
    public Integer getHttpsAcceptorThreads()
    {
        return httpsAcceptorThreads;
    }

    @Config("http-server.https.acceptor-threads")
    public HttpServerConfig setHttpsAcceptorThreads(Integer httpsAcceptorThreads)
    {
        this.httpsAcceptorThreads = httpsAcceptorThreads;
        return this;
    }

    @Min(1)
    public Integer getHttpsSelectorThreads()
    {
        return httpsSelectorThreads;
    }

    @Config("http-server.https.selector-threads")
    public HttpServerConfig setHttpsSelectorThreads(Integer httpsSelectorThreads)
    {
        this.httpsSelectorThreads = httpsSelectorThreads;
        return this;
    }

    public int getMaxThreads()
    {
        return maxThreads;
    }

    @Config("http-server.threads.max")
    public HttpServerConfig setMaxThreads(int maxThreads)
    {
        this.maxThreads = maxThreads;
        return this;
    }

    public int getMinThreads()
    {
        return minThreads;
    }

    @Config("http-server.threads.min")
    public HttpServerConfig setMinThreads(int minThreads)
    {
        this.minThreads = minThreads;
        return this;
    }

    public Duration getThreadMaxIdleTime()
    {
        return threadMaxIdleTime;
    }

    @Config("http-server.threads.max-idle-time")
    public HttpServerConfig setThreadMaxIdleTime(Duration threadMaxIdleTime)
    {
        this.threadMaxIdleTime = threadMaxIdleTime;
        return this;
    }

    public int getLogHistory()
    {
        return logHistory;
    }

    @Config("http-server.log.max-history")
    public HttpServerConfig setLogHistory(int logHistory)
    {
        this.logHistory = logHistory;
        return this;
    }

    public String getUserAuthFile()
    {
        return userAuthFile;
    }

    @Config("http-server.auth.users-file")
    public HttpServerConfig setUserAuthFile(String userAuthFile)
    {
        this.userAuthFile = userAuthFile;
        return this;
    }

    public Duration getNetworkMaxIdleTime()
    {
        return networkMaxIdleTime;
    }

    @Config("http-server.net.max-idle-time")
    public HttpServerConfig setNetworkMaxIdleTime(Duration networkMaxIdleTime)
    {
        this.networkMaxIdleTime = networkMaxIdleTime;
        return this;
    }

    public boolean isAdminEnabled()
    {
        return adminEnabled;
    }

    @Config("http-server.admin.enabled")
    public HttpServerConfig setAdminEnabled(boolean adminEnabled)
    {
        this.adminEnabled = adminEnabled;
        return this;
    }

    public int getAdminPort()
    {
        return adminPort;
    }

    @Config("http-server.admin.port")
    public HttpServerConfig setAdminPort(int adminPort)
    {
        this.adminPort = adminPort;
        return this;
    }

    public int getAdminMinThreads()
    {
        return adminMinThreads;
    }

    @Config("http-server.admin.threads.min")
    public HttpServerConfig setAdminMinThreads(int adminMinThreads)
    {
        this.adminMinThreads = adminMinThreads;
        return this;
    }

    @Min(2)
    public int getAdminMaxThreads()
    {
        return adminMaxThreads;
    }

    @Config("http-server.admin.threads.max")
    public HttpServerConfig setAdminMaxThreads(int adminMaxThreads)
    {
        this.adminMaxThreads = adminMaxThreads;
        return this;
    }

    public DataSize getMaxRequestHeaderSize()
    {
        return maxRequestHeaderSize;
    }

    @Config("http-server.max-request-header-size")
    public HttpServerConfig setMaxRequestHeaderSize(DataSize maxRequestHeaderSize)
    {
        this.maxRequestHeaderSize = maxRequestHeaderSize;
        return this;
    }

    @Min(100) // per RFC 7540 section 6.5.2
    public int getHttp2MaxConcurrentStreams()
    {
        return http2MaxConcurrentStreams;
    }

    @Config("http-server.http2.max-concurrent-streams")
    @ConfigDescription("Maximum concurrent streams per connection for HTTP/2")
    public HttpServerConfig setHttp2MaxConcurrentStreams(int http2MaxConcurrentStreams)
    {
        this.http2MaxConcurrentStreams = http2MaxConcurrentStreams;
        return this;
    }

    public boolean isShowStackTrace()
    {
        return showStackTrace;
    }

    @Config("http-server.show-stack-trace")
    @ConfigDescription("Show the stack trace when generating an error response")
    public HttpServerConfig setShowStackTrace(boolean showStackTrace)
    {
        this.showStackTrace = showStackTrace;
        return this;
    }
}
