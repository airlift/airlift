package io.airlift.security.client;

import io.airlift.configuration.Config;

public class ClientSecurityConfig
{
    private String krb5ConfPath;
    private boolean authenticationEnabled;
    private String serviceName;

    public String getKrb5ConfPath()
    {
        return krb5ConfPath;
    }

    @Config("http.authentication.negotiate.krb5conf")
    public ClientSecurityConfig setKrb5ConfPath(String krb5ConfPath)
    {
        this.krb5ConfPath = krb5ConfPath;
        return this;
    }

    public boolean getAuthenticationEnabled()
    {
        return authenticationEnabled;
    }

    @Config("http.client.authentication.enabled")
    public ClientSecurityConfig setAuthenticationEnabled(boolean enabled)
    {
        this.authenticationEnabled = enabled;
        return this;
    }

    public String getServiceName()
    {
        return serviceName;
    }

    @Config("http.client.authentication.negotiate.service-name")
    public ClientSecurityConfig setServiceName(String serviceName)
    {
        this.serviceName = serviceName;
        return this;
    }
}
