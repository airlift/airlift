package io.airlift.http.server;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import io.airlift.configuration.Config;
import io.airlift.configuration.ConfigDescription;
import io.airlift.configuration.ConfigSecuritySensitive;
import io.airlift.units.Duration;
import io.airlift.units.MinDuration;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import org.eclipse.jetty.util.ssl.SslContextFactory;

import java.util.List;

import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.HOURS;
import static java.util.concurrent.TimeUnit.MINUTES;

public class HttpsConfig
{
    private int httpsPort = 8443;

    private String httpsProvider;
    private String httpsProtocol = "TLS";
    private String keystorePath;
    private String keystorePassword;
    private String keyManagerPassword;
    private String trustStorePath;
    private String trustStorePassword;
    private String secureRandomAlgorithm;
    private List<String> includedCipherSuites = ImmutableList.of();
    private Duration sslContextRefreshTime = new Duration(1, MINUTES);
    private String automaticHttpsSharedSecret;

    private List<String> excludedCipherSuites = ImmutableList.copyOf(new SslContextFactory.Server().getExcludeCipherSuites());

    private Duration sslSessionTimeout = new Duration(4, HOURS);
    private int sslSessionCacheSize = 10_000;

    public int getHttpsPort()
    {
        return httpsPort;
    }

    @Config("http-server.https.port")
    public HttpsConfig setHttpsPort(int httpsPort)
    {
        this.httpsPort = httpsPort;
        return this;
    }

    public String getHttpsProvider()
    {
        return httpsProvider;
    }

    @Config("http-server.https.provider")
    public HttpsConfig setHttpsProvider(String httpsProvider)
    {
        this.httpsProvider = httpsProvider;
        return this;
    }

    public String getHttpsProtocol()
    {
        return httpsProtocol;
    }

    @Config("http-server.https.protocol")
    public HttpsConfig setHttpsProtocol(String httpsProtocol)
    {
        this.httpsProtocol = httpsProtocol;
        return this;
    }

    @MinDuration("1s")
    public Duration getSslSessionTimeout()
    {
        return sslSessionTimeout;
    }

    @Config("http-server.https.ssl-session-timeout")
    public HttpsConfig setSslSessionTimeout(Duration sslSessionTimeout)
    {
        this.sslSessionTimeout = sslSessionTimeout;
        return this;
    }

    @Min(1)
    public int getSslSessionCacheSize()
    {
        return sslSessionCacheSize;
    }

    @Config("http-server.https.ssl-session-cache-size")
    public HttpsConfig setSslSessionCacheSize(int sslSessionCacheSize)
    {
        this.sslSessionCacheSize = sslSessionCacheSize;
        return this;
    }

    public String getKeystorePath()
    {
        return keystorePath;
    }

    @Config("http-server.https.keystore.path")
    public HttpsConfig setKeystorePath(String keystorePath)
    {
        this.keystorePath = keystorePath;
        return this;
    }

    public String getKeystorePassword()
    {
        return keystorePassword;
    }

    @Config("http-server.https.keystore.key")
    @ConfigSecuritySensitive
    public HttpsConfig setKeystorePassword(String keystorePassword)
    {
        this.keystorePassword = keystorePassword;
        return this;
    }

    @AssertTrue(message = "Keystore path or automatic HTTPS shared secret must be provided when HTTPS is enabled")
    public boolean isHttpsConfigurationValid()
    {
        return getKeystorePath() != null || getAutomaticHttpsSharedSecret() != null;
    }

    public String getKeyManagerPassword()
    {
        return keyManagerPassword;
    }

    @Config("http-server.https.keymanager.password")
    @ConfigSecuritySensitive
    public HttpsConfig setKeyManagerPassword(String keyManagerPassword)
    {
        this.keyManagerPassword = keyManagerPassword;
        return this;
    }

    public String getTrustStorePath()
    {
        return trustStorePath;
    }

    @Config("http-server.https.truststore.path")
    public HttpsConfig setTrustStorePath(String trustStorePath)
    {
        this.trustStorePath = trustStorePath;
        return this;
    }

    public String getTrustStorePassword()
    {
        return trustStorePassword;
    }

    @Config("http-server.https.truststore.key")
    @ConfigSecuritySensitive
    public HttpsConfig setTrustStorePassword(String trustStorePassword)
    {
        this.trustStorePassword = trustStorePassword;
        return this;
    }

    public String getSecureRandomAlgorithm()
    {
        return secureRandomAlgorithm;
    }

    @Config("http-server.https.secure-random-algorithm")
    public HttpsConfig setSecureRandomAlgorithm(String secureRandomAlgorithm)
    {
        this.secureRandomAlgorithm = secureRandomAlgorithm;
        return this;
    }

    public List<String> getHttpsIncludedCipherSuites()
    {
        return includedCipherSuites;
    }

    @Config("http-server.https.included-cipher")
    public HttpsConfig setHttpsIncludedCipherSuites(String includedCipherSuites)
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
    @ConfigDescription("Setting this config property overwrites Jetty's default excluded cipher suites")
    public HttpsConfig setHttpsExcludedCipherSuites(String excludedCipherSuites)
    {
        this.excludedCipherSuites = Splitter
                .on(',')
                .trimResults()
                .omitEmptyStrings()
                .splitToList(requireNonNull(excludedCipherSuites, "excludedCipherSuites is null"));
        return this;
    }

    @MinDuration("1s")
    public Duration getSslContextRefreshTime()
    {
        return sslContextRefreshTime;
    }

    @Config("http-server.https.ssl-context.refresh-time")
    public HttpsConfig setSslContextRefreshTime(Duration sslContextRefreshTime)
    {
        this.sslContextRefreshTime = sslContextRefreshTime;
        return this;
    }

    public String getAutomaticHttpsSharedSecret()
    {
        return automaticHttpsSharedSecret;
    }

    @ConfigSecuritySensitive
    @Config("http-server.https.automatic-shared-secret")
    public HttpsConfig setAutomaticHttpsSharedSecret(String automaticHttpsSharedSecret)
    {
        this.automaticHttpsSharedSecret = automaticHttpsSharedSecret;
        return this;
    }
}
