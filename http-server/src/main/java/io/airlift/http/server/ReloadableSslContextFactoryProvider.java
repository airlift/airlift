package io.airlift.http.server;

import io.airlift.http.server.HttpServer.ClientCertificate;
import io.airlift.log.Logger;
import io.airlift.security.pem.PemReader;
import org.eclipse.jetty.util.ssl.SslContextFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;

import static com.google.common.base.Preconditions.checkArgument;
import static java.lang.Math.toIntExact;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * This class constructs and reloads an SslContextFactory.Server instance on a schedule.
 */
final class ReloadableSslContextFactoryProvider
{
    private static final Logger log = Logger.get(ReloadableSslContextFactoryProvider.class);

    private final SslContextFactory.Server sslContextFactory;

    private final String keystorePath;
    private final String keystorePassword;
    private final String keyManagerPassword;

    private final String trustStorePath;
    private final String trustStorePassword;

    public ReloadableSslContextFactoryProvider(HttpServerConfig config, ScheduledExecutorService scheduledExecutor, ClientCertificate clientCertificate)
    {
        requireNonNull(config, "config is null");
        requireNonNull(scheduledExecutor, "scheduledExecutor is null");

        keystorePath = config.getKeystorePath();
        keystorePassword = config.getKeystorePassword();
        keyManagerPassword = config.getKeyManagerPassword();

        trustStorePath = config.getTrustStorePath();
        trustStorePassword = config.getTrustStorePassword();

        sslContextFactory = new SslContextFactory.Server();
        sslContextFactory.setIncludeCipherSuites(config.getHttpsIncludedCipherSuites().toArray(new String[0]));
        sslContextFactory.setExcludeCipherSuites(config.getHttpsExcludedCipherSuites().toArray(new String[0]));
        sslContextFactory.setSecureRandomAlgorithm(config.getSecureRandomAlgorithm());
        switch (clientCertificate) {
            case NONE:
                // no changes
                break;
            case REQUESTED:
                sslContextFactory.setWantClientAuth(true);
                break;
            case REQUIRED:
                sslContextFactory.setNeedClientAuth(true);
                break;
            default:
                throw new IllegalArgumentException("Unsupported client certificate value: " + clientCertificate);
        }
        sslContextFactory.setSslSessionTimeout(toIntExact(config.getSslSessionTimeout().roundTo(SECONDS)));
        sslContextFactory.setSslSessionCacheSize(config.getSslSessionCacheSize());
        loadContextFactory(sslContextFactory);

        long refreshTime = config.getSslContextRefreshTime().toMillis();
        scheduledExecutor.scheduleWithFixedDelay(this::reload, refreshTime, refreshTime, MILLISECONDS);
    }

    private void loadContextFactory(SslContextFactory.Server sslContextFactory)
    {
        KeyStore keyStore = loadKeyStore(keystorePath, keystorePassword, keyManagerPassword);
        sslContextFactory.setKeyStore(keyStore);
        sslContextFactory.setKeyStorePassword(firstNonNullOrEmpty(keyManagerPassword, keystorePassword));

        if (trustStorePath != null) {
            sslContextFactory.setTrustStore(loadTrustStore(trustStorePath, trustStorePassword));
            sslContextFactory.setTrustStorePassword("");
        }
        else {
            // Backwards compatibility for with Jetty's internal behavior
            sslContextFactory.setTrustStore(keyStore);
            sslContextFactory.setKeyStorePassword(firstNonNullOrEmpty(keyManagerPassword, keystorePassword));
        }
    }

    private static KeyStore loadKeyStore(String keystorePath, String keystorePassword, String keyManagerPassword)
    {
        if (keystorePath == null) {
            try {
                KeyStore keyStore = KeyStore.getInstance("JKS");
                keyStore.load(null, new char[0]);
                return keyStore;
            }
            catch (GeneralSecurityException | IOException e) {
                throw new RuntimeException(e);
            }
        }

        try {
            File keyStoreFile = new File(keystorePath);
            if (PemReader.isPem(keyStoreFile)) {
                checkArgument(keyManagerPassword == null, "key manager password is not allowed with a PEM keystore");
                return PemReader.loadKeyStore(keyStoreFile, keyStoreFile, Optional.ofNullable(keystorePassword), true);
            }
        }
        catch (IOException | GeneralSecurityException e) {
            throw new IllegalArgumentException("Error loading PEM key store: " + keystorePath, e);
        }

        try (InputStream in = new FileInputStream(keystorePath)) {
            KeyStore keyStore = KeyStore.getInstance("JKS");
            keyStore.load(in, keystorePassword.toCharArray());
            return keyStore;
        }
        catch (IOException | GeneralSecurityException e) {
            throw new IllegalArgumentException("Error loading Java key store: " + keystorePath, e);
        }
    }

    private static KeyStore loadTrustStore(String truststorePath, String truststorePassword)
    {
        try {
            File keyStoreFile = new File(truststorePath);
            if (PemReader.isPem(keyStoreFile)) {
                return PemReader.loadTrustStore(keyStoreFile);
            }
        }
        catch (IOException | GeneralSecurityException e) {
            throw new IllegalArgumentException("Error loading PEM trust store: " + truststorePath, e);
        }

        try (InputStream in = new FileInputStream(truststorePath)) {
            KeyStore keyStore = KeyStore.getInstance("JKS");
            keyStore.load(in, truststorePassword == null ? null : truststorePassword.toCharArray());
            return keyStore;
        }
        catch (IOException | GeneralSecurityException e) {
            throw new IllegalArgumentException("Error loading Java trust store: " + truststorePath, e);
        }
    }

    /**
     * Returns the SslContextFactory.Server instance being managed by this instance.
     */
    public SslContextFactory.Server getSslContextFactory()
    {
        return sslContextFactory;
    }

    private synchronized void reload()
    {
        try {
            sslContextFactory.reload(sslContextFactory -> loadContextFactory((SslContextFactory.Server) sslContextFactory));
        }
        catch (Exception e) {
            log.warn(e, "Unable to reload SslContext.");
        }
    }

    private static String firstNonNullOrEmpty(String first, String second)
    {
        if (first != null) {
            return first;
        }
        if (second != null) {
            return second;
        }
        return "";
    }
}
