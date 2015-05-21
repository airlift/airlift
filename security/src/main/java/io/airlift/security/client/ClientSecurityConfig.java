package io.airlift.security.client;

import io.airlift.configuration.Config;
import io.airlift.configuration.ConfigDescription;

public class ClientSecurityConfig
{
    private String krb5Config;
    private String krb5Keytab;
    private String krb5CredentialCache;
    private String krb5Principal;
    private String krb5RemoteServiceName;
    private boolean authenticationEnabled;

    public String getKrb5CredentialCache()
    {
        return krb5CredentialCache;
    }

    @Config("http.authentication.krb5.credential-cache")
    @ConfigDescription("Set kerberos credential cache path")
    public ClientSecurityConfig setKrb5CredentialCache(String krb5CredentialCache)
    {
        this.krb5CredentialCache = krb5CredentialCache;
        return this;
    }

    public String getKrb5Keytab()
    {
        return krb5Keytab;
    }

    @Config("http.authentication.krb5.keytab")
    @ConfigDescription("Set kerberos key table path")
    public ClientSecurityConfig setKrb5Keytab(String krb5Keytab)
    {
        this.krb5Keytab = krb5Keytab;
        return this;
    }

    public String getKrb5Config()
    {
        return krb5Config;
    }

    @Config("http.authentication.krb5.config")
    @ConfigDescription("Set kerberos configuration path")
    public ClientSecurityConfig setKrb5Config(String krb5Config)
    {
        this.krb5Config = krb5Config;
        return this;
    }

    public boolean getAuthenticationEnabled()
    {
        return authenticationEnabled;
    }

    @Config("http.client.authentication.enabled")
    @ConfigDescription("Enable client authentication")
    public ClientSecurityConfig setAuthenticationEnabled(boolean authenticationEnabled)
    {
        this.authenticationEnabled = authenticationEnabled;
        return this;
    }

    public String getKrb5Principal()
    {
        return krb5Principal;
    }

    @Config("http.client.authentication.krb5.principal")
    @ConfigDescription("Set kerberos principal to be used")
    public ClientSecurityConfig setKrb5Principal(String krb5Principal)
    {
        this.krb5Principal = krb5Principal;
        return this;
    }

    public String getKrb5RemoteServiceName()
    {
        return krb5RemoteServiceName;
    }

    @Config("http.client.authentication.krb5.remote-service-name")
    @ConfigDescription("Set remote peer's kerberos service name")
    public ClientSecurityConfig setKrb5RemoteServiceName(String krb5RemoteServiceName)
    {
        this.krb5RemoteServiceName = krb5RemoteServiceName;
        return this;
    }
}
