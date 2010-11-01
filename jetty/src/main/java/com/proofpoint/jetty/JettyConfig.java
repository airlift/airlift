package com.proofpoint.jetty;

import com.proofpoint.configuration.Config;

public class JettyConfig
{
    @Config("jetty.ip")
    public String getServerIp()
    {
        return null;
    }

    @Config("jetty.http.enabled")
    public boolean isHttpEnabled()
    {
        return true;
    }

    @Config("jetty.http.port")
    public int getHttpPort()
    {
        return 8080;
    }

    @Config("jetty.https.enabled")
    public boolean isHttpsEnabled()
    {
        return false;
    }

    @Config("jetty.https.port")
    public int getHttpsPort()
    {
        return 8443;
    }

    @Config("jetty.https.keystore.path")
    public String getKeystorePath()
    {
        return null;
    }

    @Config("jetty.https.keystore.password")
    public String getKeystorePassword()
    {
        return null;
    }

    @Config("jetty.log.path")
    public String getLogPath()
    {
        return "var/log/jetty.log";
    }

    @Config("jetty.threads.max")
    public int getMaxThreads()
    {
        return 200;
    }

    @Config("jetty.threads.min")
    public int getMinThreads()
    {
        return 2;
    }

    @Config("jetty.threads.max-idle-time-ms")
    public int getThreadMaxIdleTime()
    {
        return 60000;
    }

    @Config("jetty.log.retain-days")
    public int getLogRetainDays()
    {
        return 90;
    }

    @Config("jetty.auth.users-file")
    public String getUserAuthPath()
    {
        return null;
    }
}
