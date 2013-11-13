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

import io.airlift.configuration.Config;
import io.airlift.configuration.DefunctConfig;
import io.airlift.units.DataSize;
import io.airlift.units.Duration;

import javax.validation.constraints.Min;

import java.util.concurrent.TimeUnit;

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
})
public class HttpServerConfig
{
    private boolean httpEnabled = true;
    private int httpPort = 8080;

    private boolean httpsEnabled = false;
    private int httpsPort = 8443;
    private String keystorePath;
    private String keystorePassword;

    private String logPath = "var/log/http-request.log";
    private Duration logRetentionTime = new Duration(15, TimeUnit.DAYS);

    private int minThreads = 2;
    private int maxThreads = 200;
    private Duration threadMaxIdleTime = new Duration(1, TimeUnit.MINUTES);
    private Duration networkMaxIdleTime = new Duration(200, TimeUnit.SECONDS);
    private DataSize maxRequestHeaderSize;

    private String userAuthFile;

    private boolean adminEnabled = true;
    private int adminPort = 0;
    private int adminMinThreads = 2;
    private int adminMaxThreads = 20;

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

    public Duration getLogRetentionTime()
    {
        return logRetentionTime;
    }

    @Config("http-server.log.retention-time")
    public HttpServerConfig setLogRetentionTime(Duration logRetentionTime)
    {
        this.logRetentionTime = logRetentionTime;
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
}
