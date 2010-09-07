package com.proofpoint.jetty;

import com.proofpoint.configuration.Config;

public class JettyConfig
{
    @Config("jetty.ip")
    public String getServerIp()
    {
        return null;
    }

    @Config("jetty.http.port")
    int getHttpPort()
    {
        return 8080;
    }

    @Config("jetty.https.enabled")
    boolean isHttpsEnabled()
    {
        return false;
    }

    @Config("jetty.https.port")
    int getHttpsPort()
    {
        return 8443;
    }

    @Config("jetty.https.keystore.path")
    String getKeystorePath()
    {
        return null;
    }

    @Config("jetty.https.keystore.password")
    String getKeystorePassword()
    {
        return null;
    }

    @Config("jetty.log.path")
    String getLogPath()
    {
        return "var/log/jetty.log";
    }

    @Config("jetty.threads.max")
    int getMaxThreads()
    {
        return 200;
    }

    @Config("jetty.threads.min")
    int getMinThreads()
    {
        return 2;
    }

    @Config("jetty.threads.max-idle-time-ms")
    int getThreadMaxIdleTime()
    {
        return 60000;
    }

    @Config("jetty.log.retain-days")
    int getLogRetainDays()
    {
        return 90;
    }

    @Config("jetty.auth.users-file")
    String getUserAuthPath()
    {
        return null;
    }
}
