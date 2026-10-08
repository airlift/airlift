package io.airlift.security.mtls;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.airlift.node.AddressToHostname;
import io.airlift.security.cert.CertificateBuilder;

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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECFieldFp;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPrivateKeySpec;
import java.security.spec.ECPublicKeySpec;
import java.security.spec.EllipticCurve;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Throwables.throwIfUnchecked;
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

    // The CA key pair is a pure, expensive (PBKDF2) function of (environment, sharedSecret). Memoize
    // it so repeated SSL context reloads do not repeat the derivation. The key stores a digest of the
    // shared secret rather than the secret itself, so the plaintext secret is not retained in this
    // long-lived static map.
    private static final Map<CacheKey, KeyPair> CA_KEY_PAIR_CACHE = new ConcurrentHashMap<>();

    private AutomaticMtls() {}

    private record CacheKey(String environment, String sharedSecretDigest) {}

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
            throwIfUnchecked(e);
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
            KeyPair caKeyPair = certificateAuthorityKeyPair(sharedSecret, environment);
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
            throwIfUnchecked(e);
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
            throwIfUnchecked(e);
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
            throwIfUnchecked(e);
            throw new RuntimeException(e);
        }
    }

    public static SSLContext createSSLContext(String sharedSecret, String environment, KeyStore keyStore, String keyManagerPassword)
    {
        try {
            KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(getDefaultAlgorithm());
            keyManagerFactory.init(keyStore, keyManagerPassword == null ? new char[0] : keyManagerPassword.toCharArray());

            SSLContext context = SSLContext.getInstance("TLS");
            context.init(keyManagerFactory.getKeyManagers(), new TrustManager[] {createTrustManager(sharedSecret, environment)}, null);
            return context;
        }
        catch (Exception e) {
            throwIfUnchecked(e);
            throw new RuntimeException(e);
        }
    }

    @VisibleForTesting
    static X509Certificate caCertificate(String sharedSecret, String environment)
            throws GeneralSecurityException
    {
        return buildCaCertificate(certificateAuthorityKeyPair(sharedSecret, environment), environment);
    }

    private static KeyPair certificateAuthorityKeyPair(String sharedSecret, String environment)
    {
        // Validate before hitting the cache so a short secret always fails fast with the original
        // IllegalArgumentException rather than being memoized or masked.
        checkArgument(sharedSecret.length() >= MIN_SHARED_SECRET_LENGTH,
                "automatic HTTPS shared secret must be at least %s characters; use a randomly generated high-entropy value",
                MIN_SHARED_SECRET_LENGTH);
        return CA_KEY_PAIR_CACHE.computeIfAbsent(
                new CacheKey(environment, sha256Base64(sharedSecret)),
                _ -> deriveCertificateAuthorityKeyPair(sharedSecret, environment));
    }

    private static String sha256Base64(String value)
    {
        try {
            return Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(value.getBytes(UTF_8)));
        }
        catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
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
                .setSerialNumber(randomSerialNumber())
                .setIssuer(caSubject(environment))
                .setSubject(new X500Principal("CN=" + environment))
                .setNotBefore(validityStart())
                .setNotAfter(validityEnd())
                .setCertificateAuthority(false);
    }

    // A random, non-negative 63-bit serial; serials only need to be unique per issuing CA, and a
    // random value avoids the collisions a shared wall-clock timestamp could produce across nodes.
    private static long randomSerialNumber()
    {
        return new SecureRandom().nextLong() & Long.MAX_VALUE;
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
     * arithmetic rather than by seeding a provider RNG, and the public key is computed by explicit EC
     * point multiplication (see {@link #derivePublicKey}), so the derived CA is identical across JCA
     * providers and JDK versions without depending on any third-party library.
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

            KeyFactory keyFactory = KeyFactory.getInstance("EC");
            ECPrivateKey privateKey = (ECPrivateKey) keyFactory.generatePrivate(new ECPrivateKeySpec(d, ecParameterSpec));
            ECPublicKey publicKey = derivePublicKey(d, ecParameterSpec, keyFactory);
            return new KeyPair(publicKey, privateKey);
        }
        catch (Exception e) {
            throwIfUnchecked(e);
            throw new RuntimeException(e);
        }
    }

    /**
     * Computes the public key {@code Q = d*G} for the private scalar {@code d} by explicit elliptic
     * curve point multiplication over the curve's prime field, using only the curve parameters
     * supplied by the JCA. This depends on nothing beyond {@link java.math.BigInteger} arithmetic, so
     * the derived CA is identical across JCA providers and JDK versions.
     */
    private static ECPublicKey derivePublicKey(BigInteger d, ECParameterSpec ecParameterSpec, KeyFactory keyFactory)
            throws GeneralSecurityException
    {
        EllipticCurve curve = ecParameterSpec.getCurve();
        checkState(curve.getField() instanceof ECFieldFp, "curve is not defined over a prime field");
        BigInteger p = ((ECFieldFp) curve.getField()).getP();
        ECPoint publicPoint = scalarMultiply(d, ecParameterSpec.getGenerator(), curve.getA(), p);
        return (ECPublicKey) keyFactory.generatePublic(new ECPublicKeySpec(publicPoint, ecParameterSpec));
    }

    // Double-and-add scalar multiplication on the short Weierstrass curve y^2 = x^3 + a*x + b over F_p.
    private static ECPoint scalarMultiply(BigInteger k, ECPoint generator, BigInteger a, BigInteger p)
    {
        ECPoint result = ECPoint.POINT_INFINITY;
        ECPoint addend = generator;
        for (int bit = 0; bit < k.bitLength(); bit++) {
            if (k.testBit(bit)) {
                result = pointAdd(result, addend, a, p);
            }
            addend = pointDouble(addend, a, p);
        }
        return result;
    }

    private static ECPoint pointAdd(ECPoint first, ECPoint second, BigInteger a, BigInteger p)
    {
        if (first == ECPoint.POINT_INFINITY) {
            return second;
        }
        if (second == ECPoint.POINT_INFINITY) {
            return first;
        }
        if (first.getAffineX().equals(second.getAffineX())) {
            if (first.getAffineY().add(second.getAffineY()).mod(p).signum() == 0) {
                return ECPoint.POINT_INFINITY;
            }
            return pointDouble(first, a, p);
        }
        BigInteger slope = second.getAffineY().subtract(first.getAffineY())
                .multiply(second.getAffineX().subtract(first.getAffineX()).modInverse(p))
                .mod(p);
        return pointFromSlope(slope, first, second.getAffineX(), p);
    }

    private static ECPoint pointDouble(ECPoint point, BigInteger a, BigInteger p)
    {
        if (point == ECPoint.POINT_INFINITY || point.getAffineY().signum() == 0) {
            return ECPoint.POINT_INFINITY;
        }
        BigInteger slope = point.getAffineX().pow(2).multiply(BigInteger.valueOf(3)).add(a)
                .multiply(point.getAffineY().shiftLeft(1).modInverse(p))
                .mod(p);
        return pointFromSlope(slope, point, point.getAffineX(), p);
    }

    private static ECPoint pointFromSlope(BigInteger slope, ECPoint first, BigInteger secondX, BigInteger p)
    {
        BigInteger x = slope.pow(2).subtract(first.getAffineX()).subtract(secondX).mod(p);
        BigInteger y = slope.multiply(first.getAffineX().subtract(x)).subtract(first.getAffineY()).mod(p);
        return new ECPoint(x, y);
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
