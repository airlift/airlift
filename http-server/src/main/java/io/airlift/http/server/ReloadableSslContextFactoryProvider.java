package io.airlift.http.server;

import com.google.common.collect.ImmutableList;
import com.google.common.hash.HashCode;
import com.google.common.io.Files;
import io.airlift.http.server.HttpServer.ClientCertificate;
import io.airlift.log.Logger;
import io.airlift.node.AddressToHostname;
import io.airlift.security.pem.PemReader;
import org.eclipse.jetty.util.ssl.SslContextFactory;

import javax.security.auth.x500.X500Principal;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.hash.Hashing.sha256;
import static io.airlift.security.cert.CertificateBuilder.certificateBuilder;
import static java.lang.Math.toIntExact;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.time.temporal.ChronoUnit.YEARS;
import static java.util.Collections.list;
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

    private final Optional<FileWatch> keystoreFile;
    private final String keystorePassword;
    private final String keyManagerPassword;

    private final String automaticHttpsSharedSecret;
    private final String environment;

    private final Optional<FileWatch> trustStoreFile;
    private final String trustStorePassword;

    public ReloadableSslContextFactoryProvider(HttpsConfig config, ScheduledExecutorService scheduledExecutor, ClientCertificate clientCertificate, String environment)
    {
        requireNonNull(config, "config is null");
        requireNonNull(scheduledExecutor, "scheduledExecutor is null");

        keystoreFile = Optional.ofNullable(config.getKeystorePath()).map(File::new).map(FileWatch::new);
        keystorePassword = config.getKeystorePassword();
        keyManagerPassword = config.getKeyManagerPassword();

        automaticHttpsSharedSecret = config.getAutomaticHttpsSharedSecret();
        this.environment = requireNonNull(environment, "environment is null");

        trustStoreFile = Optional.ofNullable(config.getTrustStorePath()).map(File::new).map(FileWatch::new);
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
        KeyStore keyStore = loadKeyStore(keystoreFile.map(FileWatch::getFile), keystorePassword, keyManagerPassword);

        String password = "";
        if (keyManagerPassword != null) {
            password = keyManagerPassword;
        }
        else if (keystorePassword != null) {
            password = keystorePassword;
        }

        if (automaticHttpsSharedSecret != null) {
            addAutomaticKeyForCurrentNode(automaticHttpsSharedSecret, keyStore, environment, password);
        }

        sslContextFactory.setKeyStore(keyStore);
        sslContextFactory.setKeyStorePassword(password);

        if (trustStoreFile.isPresent()) {
            sslContextFactory.setTrustStore(loadTrustStore(trustStoreFile.get().getFile(), trustStorePassword));
            sslContextFactory.setTrustStorePassword("");
        }
        else {
            // Backwards compatibility for with Jetty's internal behavior
            sslContextFactory.setTrustStore(keyStore);
            sslContextFactory.setKeyStorePassword(password);
        }
    }

    public static void addAutomaticKeyForCurrentNode(String sharedSecret, KeyStore keyStore, String commonName, String keyManagerPassword)
    {
        try {
            byte[] seed = sharedSecret.getBytes(UTF_8);
            SecureRandom secureRandom = SecureRandom.getInstance("SHA1PRNG");
            secureRandom.setSeed(seed);

            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048, secureRandom);
            KeyPair keyPair = generator.generateKeyPair();

            X500Principal subject = new X500Principal("CN=" + commonName);
            LocalDate notBefore = LocalDate.now();
            LocalDate notAfter = notBefore.plus(10, YEARS);
            List<InetAddress> allLocalIpAddresses = getAllLocalIpAddresses();
            List<String> ipAddressMappedNames = allLocalIpAddresses.stream()
                    .map(AddressToHostname::encodeAddressAsHostname)
                    .collect(toImmutableList());
            X509Certificate certificateServer = certificateBuilder()
                    .setKeyPair(keyPair)
                    .setSerialNumber(System.currentTimeMillis())
                    .setIssuer(subject)
                    .setNotBefore(notBefore)
                    .setNotAfter(notAfter)
                    .setSubject(subject)
                    .addSanIpAddresses(allLocalIpAddresses)
                    .addSanDnsNames(ipAddressMappedNames)
                    .buildSelfSigned();

            char[] password = keyManagerPassword == null ? new char[0] : keyManagerPassword.toCharArray();
            keyStore.setKeyEntry(commonName, keyPair.getPrivate(), password, new Certificate[] {certificateServer});
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static List<InetAddress> getAllLocalIpAddresses()
            throws SocketException
    {
        ImmutableList.Builder<InetAddress> list = ImmutableList.builder();
        for (NetworkInterface networkInterface : list(NetworkInterface.getNetworkInterfaces())) {
            for (InetAddress address : list(networkInterface.getInetAddresses())) {
                if (!address.isAnyLocalAddress() && !address.isLinkLocalAddress() && !address.isMulticastAddress()) {
                    list.add(address);
                }
            }
        }
        return list.build();
    }

    private static KeyStore loadKeyStore(Optional<File> keystoreFile, String keystorePassword, String keyManagerPassword)
    {
        if (!keystoreFile.isPresent()) {
            try {
                KeyStore keyStore = KeyStore.getInstance("JKS");
                keyStore.load(null, new char[0]);
                return keyStore;
            }
            catch (GeneralSecurityException | IOException e) {
                throw new RuntimeException(e);
            }
        }

        File file = keystoreFile.get();
        try {
            if (PemReader.isPem(file)) {
                checkArgument(keyManagerPassword == null, "key manager password is not allowed with a PEM keystore");
                return PemReader.loadKeyStore(file, file, Optional.ofNullable(keystorePassword), true);
            }
        }
        catch (IOException | GeneralSecurityException e) {
            throw new IllegalArgumentException("Error loading PEM key store: " + file, e);
        }

        try (InputStream in = new FileInputStream(file)) {
            KeyStore keyStore = KeyStore.getInstance("JKS");
            keyStore.load(in, Optional.ofNullable(keystorePassword).map(String::toCharArray).orElse(null));
            return keyStore;
        }
        catch (IOException | GeneralSecurityException e) {
            throw new IllegalArgumentException("Error loading Java key store: " + file, e);
        }
    }

    private static KeyStore loadTrustStore(File truststoreFile, String truststorePassword)
    {
        try {
            if (PemReader.isPem(truststoreFile)) {
                return PemReader.loadTrustStore(truststoreFile);
            }
        }
        catch (IOException | GeneralSecurityException e) {
            throw new IllegalArgumentException("Error loading PEM trust store: " + truststoreFile, e);
        }

        try (InputStream in = new FileInputStream(truststoreFile)) {
            KeyStore keyStore = KeyStore.getInstance("JKS");
            keyStore.load(in, truststorePassword == null ? null : truststorePassword.toCharArray());
            return keyStore;
        }
        catch (IOException | GeneralSecurityException e) {
            throw new IllegalArgumentException("Error loading Java trust store: " + truststoreFile, e);
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
            if (keystoreFile.map(FileWatch::updateState).orElse(false) || trustStoreFile.map(FileWatch::updateState).orElse(false)) {
                sslContextFactory.reload(sslContextFactory -> loadContextFactory((SslContextFactory.Server) sslContextFactory));
            }
        }
        catch (Exception e) {
            log.warn(e, "Unable to reload SslContext.");
        }
    }

    private static class FileWatch
    {
        private final File file;
        private long lastModified = -1;
        private long length = -1;
        private HashCode hashCode = sha256().hashBytes(new byte[0]);

        public FileWatch(File file)
        {
            this.file = requireNonNull(file, "file is null");
            updateState();
        }

        public File getFile()
        {
            return file;
        }

        public boolean updateState()
        {
            try {
                // only check contents if length or modified time changed
                long newLastModified = file.lastModified();
                long newLength = file.length();
                if (lastModified == newLastModified && length == newLength) {
                    return false;
                }

                // update stats
                lastModified = newLastModified;
                length = newLength;

                // check if contents changed
                HashCode newHashCode = Files.asByteSource(file).hash(sha256());
                if (Objects.equals(hashCode, newHashCode)) {
                    return false;
                }
                hashCode = newHashCode;
                return true;
            }
            catch (IOException e) {
                // assume the file changed
                return true;
            }
        }

        @Override
        public String toString()
        {
            return file.toString();
        }
    }
}
