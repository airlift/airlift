package com.proofpoint.jetty;

import com.proofpoint.configuration.Config;
import com.proofpoint.configuration.DeprecatedConfig;
import com.proofpoint.stats.Duration;

import java.util.concurrent.TimeUnit;

public final class JettyConfig
{
    private String ip;
    private boolean httpEnabled = true;
    private int httpPort = 8080;

    private boolean httpsEnabled = false;
    private int httpsPort = 8443;
    private String keystorePath;
    private String keystorePassword;

    private String logPath = "var/log/jetty.log";
    private Duration logRetentionTime = new Duration(90, TimeUnit.DAYS);

    private int minThreads;
    private int maxThreads;
    private Duration threadMaxIdleTime = new Duration(1, TimeUnit.MINUTES);
    private Duration networkMaxIdleTime = new Duration(200, TimeUnit.SECONDS);

    private String userAuthFile;

    @Config("jetty.ip")
    public String getIp()
    {
        return ip;
    }

    public JettyConfig setIp(String ip)
    {
        this.ip = ip;
        return this;
    }

    @Config("jetty.http.enabled")
    public boolean isHttpEnabled()
    {
        return httpEnabled;
    }

    public JettyConfig setHttpEnabled(boolean httpEnabled)
    {
        this.httpEnabled = httpEnabled;
        return this;
    }

    @Config("jetty.http.port")
    public int getHttpPort()
    {
        return httpPort;
    }

    public JettyConfig setHttpPort(int httpPort)
    {
        this.httpPort = httpPort;
        return this;
    }

    @Config("jetty.https.enabled")
    public boolean isHttpsEnabled()
    {
        return httpsEnabled;
    }

    public JettyConfig setHttpsEnabled(boolean httpsEnabled)
    {
        this.httpsEnabled = httpsEnabled;
        return this;
    }

    @Config("jetty.https.port")
    public int getHttpsPort()
    {
        return httpsPort;
    }

    public JettyConfig setHttpsPort(int httpsPort)
    {
        this.httpsPort = httpsPort;
        return this;
    }

    @Config("jetty.https.keystore.path")
    public String getKeystorePath()
    {
        return keystorePath;
    }

    public JettyConfig setKeystorePath(String keystorePath)
    {
        this.keystorePath = keystorePath;
        return this;
    }

    @Config("jetty.https.keystore.password")
    public String getKeystorePassword()
    {
        return keystorePassword;
    }

    public JettyConfig setKeystorePassword(String keystorePassword)
    {
        this.keystorePassword = keystorePassword;
        return this;
    }

    @Config("jetty.log.path")
    public String getLogPath()
    {
        return logPath;
    }

    public JettyConfig setLogPath(String logPath)
    {
        this.logPath = logPath;
        return this;
    }

    @Config("jetty.threads.max")
    public int getMaxThreads()
    {
        return maxThreads;
    }

    public JettyConfig setMaxThreads(int maxThreads)
    {
        this.maxThreads = maxThreads;
        return this;
    }

    @Config("jetty.threads.min")
    public int getMinThreads()
    {
        return minThreads;
    }

    public JettyConfig setMinThreads(int minThreads)
    {
        this.minThreads = minThreads;
        return this;
    }

    @DeprecatedConfig("jetty.threads.max-idle-time-ms")
    @Deprecated
    public JettyConfig setThreadMaxIdleTimeInMs(int millis)
    {
        return setThreadMaxIdleTime(new Duration(millis, TimeUnit.MILLISECONDS));
    }

    @Config("jetty.threads.max-idle-time")
    public Duration getThreadMaxIdleTime()
    {
        return threadMaxIdleTime;
    }

    public JettyConfig setThreadMaxIdleTime(Duration threadMaxIdleTime)
    {
        this.threadMaxIdleTime = threadMaxIdleTime;
        return this;
    }

    @DeprecatedConfig("jetty.log.retain-days")
    @Deprecated
    public JettyConfig setLogRetentionTimeInDays(int days)
    {
        return setLogRetentionTime(new Duration(days, TimeUnit.DAYS));
    }

    public Duration getLogRetentionTime()
    {
        return logRetentionTime;
    }

    @Config("jetty.log.retention-time")
    public JettyConfig setLogRetentionTime(Duration logRetentionTime)
    {
        this.logRetentionTime = logRetentionTime;
        return this;
    }

    @Config("jetty.auth.users-file")
    public String getUserAuthFile()
    {
        return userAuthFile;
    }

    public JettyConfig setUserAuthFile(String userAuthFile)
    {
        this.userAuthFile = userAuthFile;
        return this;
    }

    @DeprecatedConfig("jetty.net.max-idle-time-ms")
    @Deprecated
    public JettyConfig setNetworkMaxIdleTimeInMs(int millis)
    {
        return setNetworkMaxIdleTime(new Duration(millis, TimeUnit.MILLISECONDS));
    }

    public Duration getNetworkMaxIdleTime()
    {
        return networkMaxIdleTime;
    }

    @Config("jetty.net.max-idle-time")
    public JettyConfig setNetworkMaxIdleTime(Duration networkMaxIdleTime)
    {
        this.networkMaxIdleTime = networkMaxIdleTime;
        return this;
    }
}
