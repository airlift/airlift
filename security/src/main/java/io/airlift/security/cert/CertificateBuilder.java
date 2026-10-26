/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.airlift.security.cert;

import io.airlift.security.der.DerUtils;

import javax.security.auth.x500.X500Principal;

import java.io.ByteArrayInputStream;
import java.net.InetAddress;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.KeyPair;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.EdECKey;
import java.security.interfaces.EdECPrivateKey;
import java.security.interfaces.EdECPublicKey;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static io.airlift.security.der.DerUtils.encodeBooleanTrue;
import static io.airlift.security.der.DerUtils.encodeContextSpecificSequence;
import static io.airlift.security.der.DerUtils.encodeContextSpecificTag;
import static io.airlift.security.der.DerUtils.encodeInteger;
import static io.airlift.security.der.DerUtils.encodeNull;
import static io.airlift.security.der.DerUtils.encodeOctetString;
import static io.airlift.security.der.DerUtils.encodeOid;
import static io.airlift.security.der.DerUtils.encodeSequence;
import static io.airlift.security.der.DerUtils.encodeUtcTime;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.time.ZoneOffset.UTC;
import static java.util.Objects.requireNonNull;

public class CertificateBuilder
{
    private static final byte[] SHA_256_WITH_ECDSA_ENCRYPTION_OID = encodeOid("1.2.840.10045.4.3.2");
    private static final byte[] ED25519_OID = encodeOid("1.3.101.112");
    private static final byte[] SUBJECT_KEY_IDENTIFIER_OID = encodeOid("2.5.29.14");
    private static final byte[] AUTHORITY_KEY_IDENTIFIER_OID = encodeOid("2.5.29.35");
    private static final byte[] BASIC_CONSTRAINTS_OID = encodeOid("2.5.29.19");
    private static final byte[] SUBJECT_ALT_NAME_OID = encodeOid("2.5.29.17");

    private PublicKey publicKey;
    private PrivateKey privateKey;

    private long serialNumber;
    private X500Principal issuer;
    private Instant notBefore;
    private Instant notAfter;
    private X500Principal subject;
    private boolean certificateAuthority = true;
    private final List<String> sanDnsNames = new ArrayList<>();
    private final List<InetAddress> sanIpAddresses = new ArrayList<>();

    private CertificateBuilder() {}

    public static CertificateBuilder certificateBuilder()
    {
        return new CertificateBuilder();
    }

    public CertificateBuilder setKeyPair(KeyPair keyPair)
    {
        requireNonNull(keyPair, "keyPair is null");
        setPublicKey(keyPair.getPublic());
        setPrivateKey(keyPair.getPrivate());
        return this;
    }

    public CertificateBuilder setPublicKey(ECPublicKey publicKey)
    {
        return setPublicKey((PublicKey) publicKey);
    }

    public CertificateBuilder setPublicKey(PublicKey publicKey)
    {
        requireNonNull(publicKey, "publicKey is null");
        checkArgument(publicKey instanceof ECPublicKey || (publicKey instanceof EdECPublicKey && isEd25519(publicKey)), "not an EC or Ed25519 public key");
        this.publicKey = publicKey;
        return this;
    }

    public CertificateBuilder setPrivateKey(ECPrivateKey privateKey)
    {
        return setPrivateKey((PrivateKey) privateKey);
    }

    public CertificateBuilder setPrivateKey(PrivateKey privateKey)
    {
        requireNonNull(privateKey, "privateKey is null");
        checkArgument(privateKey instanceof ECPrivateKey || (privateKey instanceof EdECPrivateKey && isEd25519(privateKey)), "not an EC or Ed25519 private key");
        this.privateKey = privateKey;
        return this;
    }

    public CertificateBuilder setSerialNumber(long serialNumber)
    {
        checkArgument(serialNumber >= 0, "serialNumber is negative");
        this.serialNumber = serialNumber;
        return this;
    }

    public CertificateBuilder setIssuer(X500Principal issuer)
    {
        this.issuer = requireNonNull(issuer, "issuer is null");
        return this;
    }

    public CertificateBuilder setNotBefore(Instant notBefore)
    {
        this.notBefore = requireNonNull(notBefore, "notBefore is null");
        return this;
    }

    public CertificateBuilder setNotBefore(LocalDate notBefore)
    {
        requireNonNull(notBefore, "notBefore is null");
        this.notBefore = notBefore.atStartOfDay().toInstant(UTC);
        return this;
    }

    public CertificateBuilder setNotAfter(Instant notAfter)
    {
        this.notAfter = requireNonNull(notAfter, "notAfter is null");
        return this;
    }

    public CertificateBuilder setNotAfter(LocalDate notAfter)
    {
        requireNonNull(notAfter, "notAfter is null");
        this.notAfter = notAfter.atTime(23, 59, 59).toInstant(UTC);
        return this;
    }

    public CertificateBuilder setSubject(X500Principal subject)
    {
        this.subject = requireNonNull(subject, "subject is null");
        return this;
    }

    /**
     * Controls the basic constraints {@code cA} flag. Defaults to {@code true}. Set to {@code false}
     * for end-entity (leaf) certificates that must not be able to issue further certificates.
     */
    public CertificateBuilder setCertificateAuthority(boolean certificateAuthority)
    {
        this.certificateAuthority = certificateAuthority;
        return this;
    }

    public CertificateBuilder addSanIpAddress(InetAddress address)
    {
        this.sanIpAddresses.add(requireNonNull(address, "address is null"));
        return this;
    }

    public CertificateBuilder addSanIpAddresses(List<InetAddress> addresses)
    {
        requireNonNull(addresses, "addresses is null");
        addresses.forEach(this::addSanIpAddress);
        return this;
    }

    public CertificateBuilder addSanDnsName(String dnsName)
    {
        this.sanDnsNames.add(requireNonNull(dnsName, "dnsName is null"));
        return this;
    }

    public CertificateBuilder addSanDnsNames(List<String> dnsNames)
    {
        requireNonNull(dnsNames, "dnsNames is null");
        dnsNames.forEach(this::addSanDnsName);
        return this;
    }

    public X509Certificate buildSelfSigned()
            throws GeneralSecurityException
    {
        checkState(privateKey != null, "privateKey is not set");
        return build(privateKey, publicKey);
    }

    /**
     * Builds a certificate signed by the given issuing (CA) key pair rather than self-signed. The
     * {@link #setIssuer(X500Principal) issuer} must be set to the CA's subject and
     * {@code caPublicKey} is used to populate the authority key identifier.
     */
    public X509Certificate buildIssuedBy(ECPrivateKey caPrivateKey, ECPublicKey caPublicKey)
            throws GeneralSecurityException
    {
        return buildIssuedBy((PrivateKey) caPrivateKey, (PublicKey) caPublicKey);
    }

    public X509Certificate buildIssuedBy(PrivateKey caPrivateKey, PublicKey caPublicKey)
            throws GeneralSecurityException
    {
        requireNonNull(caPrivateKey, "caPrivateKey is null");
        requireNonNull(caPublicKey, "caPublicKey is null");
        return build(caPrivateKey, caPublicKey);
    }

    private X509Certificate build(PrivateKey signingKey, PublicKey authorityKey)
            throws GeneralSecurityException
    {
        checkArgument(signingKey instanceof ECPrivateKey || (signingKey instanceof EdECPrivateKey && isEd25519(signingKey)), "not an EC or Ed25519 signing key");
        checkState(publicKey != null, "publicKey is not set");
        checkState(issuer != null, "issuer is not set");
        checkState(notBefore != null, "notBefore is not set");
        checkState(notAfter != null, "notAfter is not set");
        checkState(!notBefore.isAfter(notAfter), "notAfter is before notBefore");
        checkState(subject != null, "subject is not set");

        byte[] subjectKeyHash = hashPublicKey(publicKey);
        byte[] authorityKeyHash = hashPublicKey(authorityKey);
        String signatureAlgorithm = isEd25519(signingKey) ? "Ed25519" : "SHA256withECDSA";
        // RFC 8410 requires absent parameters for Ed25519, rather than an ASN.1 NULL.
        byte[] signatureAlgorithmIdentifier = isEd25519(signingKey)
                ? encodeSequence(ED25519_OID)
                : encodeSequence(SHA_256_WITH_ECDSA_ENCRYPTION_OID, encodeNull());

        List<byte[]> sans = new ArrayList<>();
        sanDnsNames.stream()
                .map(address -> encodeContextSpecificTag(2, address.getBytes(UTF_8)))
                .forEach(sans::add);
        sanIpAddresses.stream()
                .map(InetAddress::getAddress)
                .map(address -> encodeContextSpecificTag(7, address))
                .forEach(sans::add);

        // BasicConstraints value: for a CA, SEQUENCE { cA TRUE }; for a leaf, the empty SEQUENCE
        // (cA defaults to FALSE).
        byte[] basicConstraintsValue = certificateAuthority
                ? encodeSequence(encodeBooleanTrue())
                : encodeSequence();

        List<byte[]> extensions = new ArrayList<>();
        extensions.add(encodeSequence(
                SUBJECT_KEY_IDENTIFIER_OID,
                encodeOctetString(encodeOctetString(subjectKeyHash))));
        extensions.add(encodeSequence(
                AUTHORITY_KEY_IDENTIFIER_OID,
                encodeOctetString(encodeSequence(encodeContextSpecificTag(0, authorityKeyHash)))));
        extensions.add(encodeSequence(
                BASIC_CONSTRAINTS_OID,
                encodeBooleanTrue(),
                encodeOctetString(basicConstraintsValue)));
        // Only emit SubjectAltName when there is at least one name: an empty GeneralNames SEQUENCE
        // is invalid per RFC 5280 and rejected by X.509 parsers.
        if (!sans.isEmpty()) {
            extensions.add(encodeSequence(
                    SUBJECT_ALT_NAME_OID,
                    encodeOctetString(encodeSequence(sans.toArray(new byte[0][])))));
        }

        byte[] rawCertificate = encodeSequence(
                // version: 2
                encodeContextSpecificSequence(0, encodeInteger(2)),
                // serialNumber
                encodeInteger(serialNumber),
                // signature kind
                signatureAlgorithmIdentifier,
                // issuer
                issuer.getEncoded(),
                // validity
                encodeSequence(
                        // not before
                        encodeUtcTime(notBefore),
                        // not after
                        encodeUtcTime(notAfter)),
                // subject
                subject.getEncoded(),
                // public key
                publicKey.getEncoded(),
                // extensions
                encodeContextSpecificSequence(3, encodeSequence(extensions.toArray(new byte[0][]))));

        byte[] signature = signCertificate(signingKey, signatureAlgorithm, rawCertificate);

        byte[] encodedCertificate = encodeSequence(
                rawCertificate,
                // signature kind
                signatureAlgorithmIdentifier,
                // signature
                DerUtils.encodeBitString(0, signature));

        CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
        return (X509Certificate) certificateFactory.generateCertificate(new ByteArrayInputStream(encodedCertificate));
    }

    private static byte[] signCertificate(PrivateKey signingKey, String algorithm, byte[] rawCertificate)
            throws GeneralSecurityException
    {
        Signature signature = Signature.getInstance(algorithm);
        signature.initSign(signingKey);
        signature.update(rawCertificate);
        return signature.sign();
    }

    private static byte[] hashPublicKey(PublicKey publicKey)
            throws NoSuchAlgorithmException
    {
        byte[] rawKey = publicKey.getEncoded();
        // Preserve the existing key identifier for EC certificates. For Ed25519, use the encoded
        // public key; RFC 5280 permits other methods of deriving a unique key identifier.
        if (publicKey instanceof ECPublicKey ecPublicKey) {
            rawKey = encodeSequence(
                    encodeInteger(ecPublicKey.getW().getAffineX()),
                    encodeInteger(ecPublicKey.getW().getAffineY()));
        }
        MessageDigest digest = MessageDigest.getInstance("SHA-1");
        return digest.digest(rawKey);
    }

    private static boolean isEd25519(Key key)
    {
        return key instanceof EdECKey edKey && edKey.getParams() != null && edKey.getParams().getName().equals("Ed25519");
    }
}
