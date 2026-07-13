package io.airlift.security.mtls;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.airlift.node.AddressToHostname;
import io.airlift.security.cert.CertificateBuilder;
import org.bouncycastle.jce.ECNamedCurveTable;
import org.bouncycastle.jce.spec.ECNamedCurveParameterSpec;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import javax.security.auth.x500.X500Principal;

import java.math.BigInteger;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPrivateKeySpec;
import java.security.spec.ECPublicKeySpec;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.security.KeyStore.getDefaultType;
import static java.time.ZoneOffset.UTC;
import static java.time.temporal.ChronoUnit.DAYS;
import static java.util.Collections.list;
import static javax.net.ssl.KeyManagerFactory.getDefaultAlgorithm;

/**
 * Automatic mutual TLS for an environment sharing a single secret.
 *
 * <p>A certificate authority (CA) key pair is derived deterministically from the shared secret, so
 * every node derives the same CA. Each node then generates its <em>own random</em> leaf key pair and
 * issues itself a leaf certificate signed by that CA. Peers trust each other with standard PKIX by
 * pinning the derived CA certificate as a trust anchor: presenting a valid leaf requires a signature
 * from the CA, which requires knowledge of the shared secret.
 *
 * <p>This means the private key that terminates each TLS connection is unique per node and never
 * leaves it, and is not recoverable from the shared secret; only the ability to <em>mint</em> new
 * identities depends on the secret.
 */
public final class AutomaticMtls
{
    private static final String CURVE_NAME = "secp256r1";

    // The CA key pair must be byte-for-byte identical on every node, so the derivation parameters are
    // fixed constants (never configuration): two nodes deriving different CA keys could not trust each
    // other. The "v2" tag versions the derivation scheme; bump it if the algorithm ever changes.
    private static final String KDF_SALT_PREFIX = "airlift-automatic-mtls-v2:";
    private static final int KDF_ITERATIONS = 600_000; // OWASP 2023 floor for PBKDF2-HMAC-SHA256
    private static final int MODULO_BIAS_MARGIN_BITS = 64; // keeps scalar reduction bias below 2^-64
    private static final int MIN_SHARED_SECRET_LENGTH = 32;

    private AutomaticMtls() {}

    /**
     * Generates a random leaf key pair for the current node, issues it a CA-signed certificate that
     * includes the node's local addresses as subject alternative names, and stores the private key
     * with its {@code [leaf, CA]} chain in the key store. The derived CA certificate is also added as
     * a trusted entry so that a key store used directly as a trust store anchors peer verification.
     */
    @CanIgnoreReturnValue
    public static X509Certificate addCertificateAndKeyForCurrentNode(String sharedSecret, String environment, KeyStore keyStore, String keyStorePassword)
    {
        try {
            List<InetAddress> allLocalIpAddresses = getAllLocalIpAddresses();
            List<String> ipAddressMappedNames = allLocalIpAddresses.stream()
                    .map(AddressToHostname::encodeAddressAsHostname)
                    .collect(toImmutableList());
            return addNodeCertificateAndKey(sharedSecret, environment, keyStore, keyStorePassword, allLocalIpAddresses, ipAddressMappedNames);
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @VisibleForTesting
    static X509Certificate addNodeCertificateAndKey(
            String sharedSecret,
            String environment,
            KeyStore keyStore,
            String keyStorePassword,
            List<InetAddress> ipSubjectAltNames,
            List<String> dnsSubjectAltNames)
    {
        try {
            KeyPair caKeyPair = deriveCertificateAuthorityKeyPair(sharedSecret, environment);
            X509Certificate caCertificate = buildCaCertificate(caKeyPair, environment);

            KeyPair leafKeyPair = generateRandomEcKeyPair();
            X509Certificate leafCertificate = leafCertificateBuilder(environment)
                    .setKeyPair(leafKeyPair)
                    .addSanIpAddresses(ipSubjectAltNames)
                    .addSanDnsNames(dnsSubjectAltNames)
                    .buildIssuedBy((ECPrivateKey) caKeyPair.getPrivate(), (ECPublicKey) caKeyPair.getPublic());

            char[] password = keyStorePassword == null ? new char[0] : keyStorePassword.toCharArray();
            keyStore.setKeyEntry(environment, leafKeyPair.getPrivate(), password, new Certificate[] {leafCertificate, caCertificate});
            // Anchor for the case where this key store is used directly as a trust store.
            keyStore.setCertificateEntry(environment + "-ca", caCertificate);
            return leafCertificate;
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Adds the derived CA certificate to the given (trust) store as a trust anchor, so that leaf
     * certificates issued by any node in the environment are trusted via standard PKIX validation.
     */
    public static void addClientTrust(String sharedSecret, KeyStore keyStore, String environment)
    {
        try {
            keyStore.setCertificateEntry(environment, caCertificate(sharedSecret, environment));
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Returns a standard PKIX trust manager anchored on the derived CA certificate.
     */
    public static X509TrustManager createTrustManager(String sharedSecret, String environment)
    {
        try {
            KeyStore trustStore = inMemoryKeyStore();
            trustStore.setCertificateEntry(environment, caCertificate(sharedSecret, environment));

            TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init(trustStore);
            return Arrays.stream(trustManagerFactory.getTrustManagers())
                    .filter(X509TrustManager.class::isInstance)
                    .map(X509TrustManager.class::cast)
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("No X509TrustManager available"));
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static SSLContext createSSLContext(String sharedSecret, String environment, KeyStore keyStore, String keyManagerPassword)
    {
        try {
            KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(getDefaultAlgorithm());
            keyManagerFactory.init(keyStore, keyManagerPassword.toCharArray());

            SSLContext context = SSLContext.getInstance("TLS");
            context.init(keyManagerFactory.getKeyManagers(), new TrustManager[] {createTrustManager(sharedSecret, environment)}, null);
            return context;
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @VisibleForTesting
    static X509Certificate caCertificate(String sharedSecret, String environment)
            throws GeneralSecurityException
    {
        return buildCaCertificate(deriveCertificateAuthorityKeyPair(sharedSecret, environment), environment);
    }

    private static X509Certificate buildCaCertificate(KeyPair caKeyPair, String environment)
            throws GeneralSecurityException
    {
        X500Principal caSubject = caSubject(environment);
        return CertificateBuilder.certificateBuilder()
                .setKeyPair(caKeyPair)
                .setSerialNumber(1)
                .setIssuer(caSubject)
                .setSubject(caSubject)
                .setNotBefore(validityStart())
                .setNotAfter(validityEnd())
                .setCertificateAuthority(true)
                .buildSelfSigned();
    }

    private static CertificateBuilder leafCertificateBuilder(String environment)
    {
        return CertificateBuilder.certificateBuilder()
                .setSerialNumber(System.currentTimeMillis())
                .setIssuer(caSubject(environment))
                .setSubject(new X500Principal("CN=" + environment))
                .setNotBefore(validityStart())
                .setNotAfter(validityEnd())
                .setCertificateAuthority(false);
    }

    private static Instant validityStart()
    {
        return Instant.now().truncatedTo(DAYS);
    }

    private static Instant validityEnd()
    {
        return validityStart().atZone(UTC).plusYears(10).toInstant();
    }

    private static X500Principal caSubject(String environment)
    {
        return new X500Principal("CN=" + environment + ", OU=Airlift Automatic mTLS CA");
    }

    private static KeyPair generateRandomEcKeyPair()
            throws GeneralSecurityException
    {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec(CURVE_NAME));
        return generator.generateKeyPair();
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

    /**
     * Deterministically derives the environment's CA key pair from the shared secret so that every
     * node derives the same CA. The secret is stretched through a salted, high-iteration KDF: a
     * captured certificate lets an attacker verify a guessed secret offline, so without stretching a
     * weak secret could be brute-forced cheaply. The salt binds the derivation to the environment so
     * that work cannot be amortized across clusters. The private scalar is computed with fixed
     * arithmetic rather than by seeding a provider RNG, making the result independent of the JCE
     * provider in use.
     */
    private static KeyPair deriveCertificateAuthorityKeyPair(String sharedSecret, String environment)
    {
        checkArgument(sharedSecret.length() >= MIN_SHARED_SECRET_LENGTH,
                "automatic HTTPS shared secret must be at least %s characters; use a randomly generated high-entropy value",
                MIN_SHARED_SECRET_LENGTH);
        try {
            AlgorithmParameters algorithmParameters = AlgorithmParameters.getInstance("EC");
            algorithmParameters.init(new ECGenParameterSpec(CURVE_NAME));
            ECParameterSpec ecParameterSpec = algorithmParameters.getParameterSpec(ECParameterSpec.class);
            BigInteger order = ecParameterSpec.getOrder();

            // Stretch the secret with PBKDF2, requesting enough extra bits that reducing into
            // [1, order-1] introduces negligible modulo bias.
            int derivedBits = order.bitLength() + MODULO_BIAS_MARGIN_BITS;
            byte[] salt = (KDF_SALT_PREFIX + environment).getBytes(UTF_8);
            PBEKeySpec keySpec = new PBEKeySpec(sharedSecret.toCharArray(), salt, KDF_ITERATIONS, derivedBits);
            byte[] derived = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(keySpec).getEncoded();

            // Map the derived bytes to a valid private scalar d in [1, order-1].
            BigInteger d = new BigInteger(1, derived).mod(order.subtract(BigInteger.ONE)).add(BigInteger.ONE);

            // Compute the public point Q = d*G with fixed curve arithmetic and convert back to JCE keys.
            ECNamedCurveParameterSpec bcCurve = ECNamedCurveTable.getParameterSpec(CURVE_NAME);
            org.bouncycastle.math.ec.ECPoint q = bcCurve.getG().multiply(d).normalize();
            ECPoint publicPoint = new ECPoint(
                    q.getAffineXCoord().toBigInteger(),
                    q.getAffineYCoord().toBigInteger());

            KeyFactory keyFactory = KeyFactory.getInstance("EC");
            ECPrivateKey privateKey = (ECPrivateKey) keyFactory.generatePrivate(new ECPrivateKeySpec(d, ecParameterSpec));
            ECPublicKey publicKey = (ECPublicKey) keyFactory.generatePublic(new ECPublicKeySpec(publicPoint, ecParameterSpec));
            return new KeyPair(publicKey, privateKey);
        }
        catch (Exception e) {
            throw new RuntimeException(e);
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
