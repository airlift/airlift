package com.proofpoint.http.server;

import com.proofpoint.configuration.Config;
import com.proofpoint.configuration.LegacyConfig;
import com.proofpoint.units.Duration;

import java.util.concurrent.TimeUnit;

public class HttpServerConfig
{
    private String ip;
    private boolean httpEnabled = true;
    private int httpPort = 8080;

    private boolean httpsEnabled = false;
    private int httpsPort = 8443;
    private String keystorePath;
    private String keystorePassword;

    private String logPath = "var/log/http-request.log";
    private Duration logRetentionTime = new Duration(90, TimeUnit.DAYS);

    private int minThreads = 2;
    private int maxThreads = 200;
    private Duration threadMaxIdleTime = new Duration(1, TimeUnit.MINUTES);
    private Duration networkMaxIdleTime = new Duration(200, TimeUnit.SECONDS);

    private String userAuthFile;

    public String getIp()
    {
        return ip;
    }

    @Config("http-server.ip")
    @LegacyConfig("jetty.ip")
    public HttpServerConfig setIp(String ip)
    {
        this.ip = ip;
        return this;
    }

    public boolean isHttpEnabled()
    {
        return httpEnabled;
    }

    @Config("http-server.http.enabled")
    @LegacyConfig("jetty.http.enabled")
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
    @LegacyConfig("jetty.http.port")
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
    @LegacyConfig("jetty.https.enabled")
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
    @LegacyConfig("jetty.https.port")
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
    @LegacyConfig("jetty.https.keystore.path")
    public HttpServerConfig setKeystorePath(String keystorePath)
    {
        this.keystorePath = keystorePath;
        return this;
    }

    public String getKeystorePassword()
    {
        return keystorePassword;
    }

    @Config("http-server.https.keystore.password")
    @LegacyConfig("jetty.https.keystore.password")
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
    @LegacyConfig("jetty.log.path")
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
    @LegacyConfig("jetty.threads.max")
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
    @LegacyConfig("jetty.threads.min")
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

    @Deprecated
    @LegacyConfig(value = "jetty.threads.max-idle-time-ms", replacedBy = "http-server.threads.max-idle-time")
    public HttpServerConfig setThreadMaxIdleTimeInMs(int millis)
    {
        return setThreadMaxIdleTime(new Duration(millis, TimeUnit.MILLISECONDS));
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

    @Deprecated
    @LegacyConfig(value = "jetty.log.retain-days", replacedBy = "http-server.log.retention-time")
    public HttpServerConfig setLogRetentionTimeInDays(int days)
    {
        return setLogRetentionTime(new Duration(days, TimeUnit.DAYS));
    }

    public String getUserAuthFile()
    {
        return userAuthFile;
    }

    @Config("http-server.auth.users-file")
    @LegacyConfig("jetty.auth.users-file")
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

    @Deprecated
    @LegacyConfig(value = "jetty.net.max-idle-time-ms", replacedBy = "http-server.net.max-idle-time")
    public HttpServerConfig setNetworkMaxIdleTimeInMs(int millis)
    {
        return setNetworkMaxIdleTime(new Duration(millis, TimeUnit.MILLISECONDS));
    }
}
