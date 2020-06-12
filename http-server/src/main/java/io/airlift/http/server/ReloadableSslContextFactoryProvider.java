package io.airlift.http.server;

import io.airlift.log.Logger;
import io.airlift.security.pem.PemReader;
import org.eclipse.jetty.util.ssl.SslContextFactory;

import java.io.File;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * This class constructs and reloads an SslContextFactory.Server instance on a schedule.
 */
class ReloadableSslContextFactoryProvider
{
    private static final Logger log = Logger.get(ReloadableSslContextFactoryProvider.class);

    private final SslContextFactory.Server sslContextFactory;
    private final HttpServerConfig config;

    public ReloadableSslContextFactoryProvider(HttpServerConfig config, ScheduledExecutorService scheduledExecutor)
    {
        this.config = config;
        this.sslContextFactory = buildContextFactory(config);
        long refreshTime = config.getSslContextRefreshTime().toMillis();
        scheduledExecutor.scheduleWithFixedDelay(this::reload, refreshTime, refreshTime, MILLISECONDS);
    }

    private static SslContextFactory.Server buildContextFactory(HttpServerConfig config)
    {
        List<String> includedCipherSuites = config.getHttpsIncludedCipherSuites();
        List<String> excludedCipherSuites = config.getHttpsExcludedCipherSuites();

        SslContextFactory.Server sslContextFactory = new SslContextFactory.Server();
        Optional<KeyStore> pemKeyStore = tryLoadPemKeyStore(config);
        if (pemKeyStore.isPresent()) {
            sslContextFactory.setKeyStore(pemKeyStore.get());
            sslContextFactory.setKeyStorePassword("");
        }
        else {
            sslContextFactory.setKeyStorePath(config.getKeystorePath());
            sslContextFactory.setKeyStorePassword(config.getKeystorePassword());
            if (config.getKeyManagerPassword() != null) {
                sslContextFactory.setKeyManagerPassword(config.getKeyManagerPassword());
            }
        }
        if (config.getTrustStorePath() != null) {
            Optional<KeyStore> pemTrustStore = tryLoadPemTrustStore(config);
            if (pemTrustStore.isPresent()) {
                sslContextFactory.setTrustStore(pemTrustStore.get());
                sslContextFactory.setTrustStorePassword("");
            }
            else {
                sslContextFactory.setTrustStorePath(config.getTrustStorePath());
                sslContextFactory.setTrustStorePassword(config.getTrustStorePassword());
            }
        }
        sslContextFactory.setIncludeCipherSuites(includedCipherSuites.toArray(new String[0]));
        sslContextFactory.setExcludeCipherSuites(excludedCipherSuites.toArray(new String[0]));
        sslContextFactory.setSecureRandomAlgorithm(config.getSecureRandomAlgorithm());
        sslContextFactory.setWantClientAuth(true);
        sslContextFactory.setSslSessionTimeout((int) config.getSslSessionTimeout().getValue(SECONDS));
        sslContextFactory.setSslSessionCacheSize(config.getSslSessionCacheSize());

        return sslContextFactory;
    }

    private static Optional<KeyStore> tryLoadPemKeyStore(HttpServerConfig config)
    {
        File keyStoreFile = new File(config.getKeystorePath());
        try {
            if (!PemReader.isPem(keyStoreFile)) {
                return Optional.empty();
            }
        }
        catch (IOException e) {
            throw new IllegalArgumentException("Error reading key store file: " + keyStoreFile, e);
        }

        try {
            return Optional.of(PemReader.loadKeyStore(keyStoreFile, keyStoreFile, Optional.ofNullable(config.getKeystorePassword())));
        }
        catch (IOException | GeneralSecurityException e) {
            throw new IllegalArgumentException("Error loading PEM key store: " + keyStoreFile, e);
        }
    }

    private static Optional<KeyStore> tryLoadPemTrustStore(HttpServerConfig config)
    {
        File trustStoreFile = new File(config.getTrustStorePath());
        try {
            if (!PemReader.isPem(trustStoreFile)) {
                return Optional.empty();
            }
        }
        catch (IOException e) {
            throw new IllegalArgumentException("Error reading trust store file: " + trustStoreFile, e);
        }

        try {
            if (PemReader.readCertificateChain(trustStoreFile).isEmpty()) {
                throw new IllegalArgumentException("PEM trust store file does not contain any certificates: " + trustStoreFile);
            }
            return Optional.of(PemReader.loadTrustStore(trustStoreFile));
        }
        catch (IOException | GeneralSecurityException e) {
            throw new IllegalArgumentException("Error loading PEM trust store: " + trustStoreFile, e);
        }
    }

    /**
     * Returns the SslContextFactory.Server instance being managed by this instance.
     */
    public SslContextFactory.Server getSslContextFactory()
    {
        return this.sslContextFactory;
    }

    public synchronized void reload()
    {
        try {
            SslContextFactory.Server updatedFactory = buildContextFactory(this.config);
            updatedFactory.start();
            this.sslContextFactory.reload(factory -> factory.setSslContext(updatedFactory.getSslContext()));
        }
        catch (Exception e) {
            log.warn(e, "Unable to reload SslContext.");
        }
    }
}
