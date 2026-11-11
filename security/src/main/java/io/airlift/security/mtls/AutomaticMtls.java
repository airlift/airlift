package io.airlift.security.mtls;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.airlift.node.AddressToHostname;
import io.airlift.security.cert.CertificateBuilder;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.security.auth.x500.X500Principal;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.security.spec.ECGenParameterSpec;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.security.KeyStore.getDefaultType;
import static java.time.ZoneOffset.UTC;
import static java.time.temporal.ChronoUnit.DAYS;
import static java.util.Collections.list;
import static java.util.Objects.requireNonNull;
import static javax.net.ssl.KeyManagerFactory.getDefaultAlgorithm;

public final class AutomaticMtls
{
    // Key derivation is deterministic but expensive (PBKDF2 work factor), so cache per process
    private static final LoadingCache<DerivationKey, KeyPair> KEY_PAIR_CACHE = CacheBuilder.newBuilder()
            .maximumSize(256)
            .build(CacheLoader.from(key -> deriveKeyPair(key.sharedSecret(), key.commonName())));

    private AutomaticMtls() {}

    private record DerivationKey(String sharedSecret, String commonName) {}

    @CanIgnoreReturnValue
    public static X509Certificate addCertificateAndKeyForCurrentNode(String sharedSecret, String commonName, KeyStore keyStore, String keyStorePassword)
    {
        try {
            List<InetAddress> allLocalIpAddresses = getAllLocalIpAddresses();
            List<String> ipAddressMappedNames = allLocalIpAddresses.stream()
                    .map(AddressToHostname::encodeAddressAsHostname)
                    .collect(toImmutableList());
            X509Certificate certificate = certificateBuilder(sharedSecret, commonName)
                    .addSanIpAddresses(allLocalIpAddresses)
                    .addSanDnsNames(ipAddressMappedNames)
                    .buildSelfSigned();

            return addCertificateToKeyStore(sharedSecret, commonName, certificate, keyStore, keyStorePassword);
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @VisibleForTesting
    static X509Certificate addCertificateToKeyStore(String sharedSecret, String commonName, X509Certificate certificate, KeyStore keyStore, String keyStorePassword)
    {
        try {
            KeyPair keyPair = fromSharedSecret(sharedSecret, commonName);
            char[] password = keyStorePassword == null ? new char[0] : keyStorePassword.toCharArray();
            keyStore.setKeyEntry(commonName, keyPair.getPrivate(), password, new Certificate[] {certificate});
            return certificate;
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void addClientTrust(String sharedSecret, KeyStore keyStore, String commonName)
    {
        try {
            X509Certificate certificateServer = certificateBuilder(sharedSecret, commonName)
                    .buildSelfSigned();

            keyStore.setCertificateEntry(commonName, certificateServer);
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static X509TrustManager createTrustManager(String sharedSecret, String commonName)
    {
        try {
            KeyPair keyPair = fromSharedSecret(sharedSecret, commonName);
            return new SingleCertificateTrustManager(keyPair.getPublic(), commonName);
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static SSLContext createSSLContext(String sharedSecret, String commonName, KeyStore keyStore, String keyManagerPassword)
    {
        try {
            KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(getDefaultAlgorithm());
            keyManagerFactory.init(keyStore, keyManagerPassword.toCharArray());

            SSLContext context = SSLContext.getInstance("TLS");
            context.init(keyManagerFactory.getKeyManagers(), new TrustManager[] {createTrustManager(sharedSecret, commonName)}, null);
            return context;
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

    private static KeyPair fromSharedSecret(String sharedSecret, String commonName)
    {
        return KEY_PAIR_CACHE.getUnchecked(new DerivationKey(sharedSecret, commonName));
    }

    private static KeyPair deriveKeyPair(String sharedSecret, String commonName)
    {
        try {
            // Every parameter of this derivation is part of the compatibility contract: peers derive
            // the same key pair independently from the shared secret, so any change here breaks mTLS
            // with nodes running a previous derivation (bump the salt version when changing it)
            PBEKeySpec keySpec = new PBEKeySpec(
                    sharedSecret.toCharArray(),
                    ("io.airlift.security.mtls.v2:" + commonName).getBytes(UTF_8),
                    600_000,
                    256);
            byte[] seed = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(keySpec)
                    .getEncoded();

            // SHA1PRNG from the SUN provider is fully deterministic when seeded before first use;
            // other providers offer no such guarantee, so fail rather than fall back to one
            SecureRandom secureRandom = SecureRandom.getInstance("SHA1PRNG", "SUN");
            secureRandom.setSeed(seed);

            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(new ECGenParameterSpec("secp256r1"), secureRandom);
            return generator.generateKeyPair();
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @VisibleForTesting
    static CertificateBuilder certificateBuilder(String sharedSecret, String commonName)
    {
        KeyPair keyPair = fromSharedSecret(sharedSecret, commonName);
        Instant notBefore = Instant.now().truncatedTo(DAYS);
        Instant notAfter = notBefore.atZone(UTC).plusYears(10).toInstant();
        X500Principal subject = certificateSubject(commonName);
        return CertificateBuilder.certificateBuilder()
                .setKeyPair(keyPair)
                .setSerialNumber(System.currentTimeMillis())
                .setIssuer(subject)
                .setNotBefore(notBefore)
                .setNotAfter(notAfter)
                .setSubject(subject);
    }

    private static X500Principal certificateSubject(String commonName)
    {
        return new X500Principal("CN=" + commonName);
    }

    private record SingleCertificateTrustManager(PublicKey trustedPublicKey, String commonName)
            implements X509TrustManager
    {
        private SingleCertificateTrustManager(PublicKey trustedPublicKey, String commonName)
        {
            this.trustedPublicKey = requireNonNull(trustedPublicKey, "trustedPublicKey is null");
            this.commonName = requireNonNull(commonName, "commonName is null");
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType)
        {
            check(chain);
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType)
        {
            check(chain);
        }

        private void check(X509Certificate[] chain)
        {
            if (chain == null || chain.length == 0) {
                throw new SecurityException("Empty certificate chain");
            }
            if (chain.length > 1) {
                throw new SecurityException("Certificate chain must contain only one certificate");
            }
            X509Certificate certificate = chain[0];
            PublicKey peerKey = certificate.getPublicKey();
            if (!Arrays.equals(peerKey.getEncoded(), trustedPublicKey.getEncoded())) {
                throw new SecurityException("Peer cert public key doesn't match trusted key");
            }

            X500Principal peerSubject = certificate.getSubjectX500Principal();
            X500Principal expectedSubject = certificateSubject(commonName);
            if (!peerSubject.equals(expectedSubject)) {
                throw new SecurityException("Peer certificate subject '%s' does not match expected subject: '%s'".formatted(peerSubject, expectedSubject));
            }

            X500Principal peerIssuer = certificate.getIssuerX500Principal();
            if (!peerIssuer.equals(expectedSubject)) {
                throw new SecurityException("Peer certificate issuer '%s' does not match expected subject: '%s'".formatted(peerIssuer, expectedSubject));
            }

            Instant now = Instant.now();
            if (certificate.getNotBefore().toInstant().isAfter(now)) {
                throw new SecurityException("Peer certificate is not valid yet (notBefore: %s)".formatted(certificate.getNotBefore()));
            }

            if (certificate.getNotAfter().toInstant().isBefore(now)) {
                throw new SecurityException("Peer certificate has expired (notAfter: %s)".formatted(certificate.getNotAfter()));
            }
        }

        @Override
        public X509Certificate[] getAcceptedIssuers()
        {
            return new X509Certificate[0];
        }
    }

    public static KeyStore inMemoryKeyStore()
    {
        try {
            KeyStore keyStore = KeyStore.getInstance(getDefaultType());
            keyStore.load(null, null);
            return keyStore;
        }
        catch (Exception e) {
            throw new RuntimeException("Failed to create in-memory keystore", e);
        }
    }
}
