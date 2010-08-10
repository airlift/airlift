package com.proofpoint.jetty;

public interface JettyConfig
{
//    @Config("jetty.ip")
//    @Default("")
    String getServerIp();

//    @Config("jetty.http.port")
//    @Default("8080")
    int getHttpPort();

//    @Config("jetty.https.enabled")
//    @Default("false")
     boolean isHttpsEnabled();

//    @Config("jetty.https.port")
//    @Default("8443")
    int getHttpsPort();

//    @Config("jetty.https.keystore.path")
//    @Default("")
    String getKeystorePath();

//    @Config("jetty.https.keystore.password")
//    @Default("")
    String getKeystorePassword();

//    @Config("jetty.log.path")
//    @Default("var/log/jetty.log")
    String getLogPath();

//    @Config("jetty.threads.max")
//    @Default("200")
    int getMaxThreads();

//    @Config("jetty.threads.min")
//    @Default("2")
    int getMinThreads();

//    @Config("jetty.threads.max-idle-time-ms")
//    @Default("60000")
    int getThreadMaxIdleTime();

//    @Config("jetty.log.retain-days")
//    @Default("90")
    int getLogRetainDays();

    /**
     * @return config parameter to realm constructor
     */
//    @Config("jetty.auth.users-file")
//    @Default("")
    String getUserAuthPath();
}
