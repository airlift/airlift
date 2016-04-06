package io.airlift.http.client.spnego;

import io.airlift.configuration.Config;
import io.airlift.configuration.ConfigDescription;

import java.io.File;

public class KerberosConfig
{
    private File credentialCache;
    private File keytab;
    private File config;
    private boolean useCanonicalHostname = true;

    public File getCredentialCache()
    {
        return credentialCache;
    }

    @Config("http.authentication.krb5.credential-cache")
    @ConfigDescription("Set kerberos credential cache path")
    public KerberosConfig setCredentialCache(File credentialCache)
    {
        this.credentialCache = credentialCache;
        return this;
    }

    public File getKeytab()
    {
        return keytab;
    }

    @Config("http.authentication.krb5.keytab")
    @ConfigDescription("Set kerberos key table path")
    public KerberosConfig setKeytab(File keytab)
    {
        this.keytab = keytab;
        return this;
    }

    public File getConfig()
    {
        return config;
    }

    @Config("http.authentication.krb5.config")
    @ConfigDescription("Set kerberos configuration path")
    public KerberosConfig setConfig(File config)
    {
        this.config = config;
        return this;
    }

    public boolean isUseCanonicalHostname()
    {
        return useCanonicalHostname;
    }

    @Config("http.authentication.krb5.use-canonical-hostname")
    @ConfigDescription("Canonicalize service hostname using the DNS reverse lookup")
    public KerberosConfig setUseCanonicalHostname(boolean useCanonicalHostname)
    {
        this.useCanonicalHostname = useCanonicalHostname;
        return this;
    }
}
