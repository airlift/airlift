package io.airlift.http.server;

import io.airlift.http.server.HttpServer.ClientCertificate;
import io.airlift.log.Logger;
import io.airlift.security.pem.PemReader;
import org.eclipse.jetty.util.ssl.SslContextFactory;

import java.io.File;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;

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

    private void loadContextFactory(SslContextFactory sslContextFactory)
    {
        Optional<KeyStore> pemKeyStore = tryLoadPemKeyStore(keystorePath, keystorePassword);
        if (pemKeyStore.isPresent()) {
            sslContextFactory.setKeyStore(pemKeyStore.get());
            sslContextFactory.setKeyStorePassword("");
        }
        else {
            sslContextFactory.setKeyStorePath(keystorePath);
            sslContextFactory.setKeyStorePassword(keystorePassword);
            if (keyManagerPassword != null) {
                sslContextFactory.setKeyManagerPassword(keyManagerPassword);
            }
        }
        if (trustStorePath != null) {
            Optional<KeyStore> pemTrustStore = tryLoadPemTrustStore(trustStorePath);
            if (pemTrustStore.isPresent()) {
                sslContextFactory.setTrustStore(pemTrustStore.get());
                sslContextFactory.setTrustStorePassword("");
            }
            else {
                sslContextFactory.setTrustStorePath(trustStorePath);
                sslContextFactory.setTrustStorePassword(trustStorePassword);
            }
        }
    }

    private static Optional<KeyStore> tryLoadPemKeyStore(String path, String password)
    {
        File keyStoreFile = new File(path);
        try {
            if (!PemReader.isPem(keyStoreFile)) {
                return Optional.empty();
            }
        }
        catch (IOException e) {
            throw new IllegalArgumentException("Error reading key store file: " + keyStoreFile, e);
        }

        try {
            return Optional.of(PemReader.loadKeyStore(keyStoreFile, keyStoreFile, Optional.ofNullable(password)));
        }
        catch (IOException | GeneralSecurityException e) {
            throw new IllegalArgumentException("Error loading PEM key store: " + keyStoreFile, e);
        }
    }

    private static Optional<KeyStore> tryLoadPemTrustStore(String path)
    {
        File trustStoreFile = new File(path);
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
        return sslContextFactory;
    }

    private synchronized void reload()
    {
        try {
            sslContextFactory.reload(this::loadContextFactory);
        }
        catch (Exception e) {
            log.warn(e, "Unable to reload SslContext.");
        }
    }
}
