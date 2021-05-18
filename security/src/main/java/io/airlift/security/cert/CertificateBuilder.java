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
import java.security.KeyPair;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Signature;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
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
    private static final byte[] SHA_256_WITH_RSA_ENCRYPTION_OID = encodeOid("1.2.840.113549.1.1.11");
    private static final byte[] SUBJECT_KEY_IDENTIFIER_OID = encodeOid("2.5.29.14");
    private static final byte[] AUTHORITY_KEY_IDENTIFIER_OID = encodeOid("2.5.29.35");
    private static final byte[] BASIC_CONSTRAINTS_OID = encodeOid("2.5.29.19");
    private static final byte[] SUBJECT_ALT_NAME_OID = encodeOid("2.5.29.17");

    private RSAPublicKey publicKey;
    private RSAPrivateKey privateKey;

    private long serialNumber;
    private X500Principal issuer;
    private Instant notBefore;
    private Instant notAfter;
    private X500Principal subject;
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
        checkArgument(keyPair.getPublic() instanceof RSAPublicKey, "not an RSA key: %s", keyPair.getPublic());
        checkArgument(keyPair.getPrivate() instanceof RSAPrivateKey, "not an RSA key: %s", keyPair.getPrivate());
        setPublicKey((RSAPublicKey) keyPair.getPublic());
        setPrivateKey((RSAPrivateKey) keyPair.getPrivate());
        return this;
    }

    public CertificateBuilder setPublicKey(RSAPublicKey publicKey)
    {
        this.publicKey = requireNonNull(publicKey, "publicKey is null");
        return this;
    }

    public CertificateBuilder setPrivateKey(RSAPrivateKey privateKey)
    {
        this.privateKey = requireNonNull(privateKey, "privateKey is null");
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

    public CertificateBuilder addSanDnsNamees(List<String> dnsNames)
    {
        requireNonNull(dnsNames, "dnsNames is null");
        dnsNames.forEach(this::addSanDnsName);
        return this;
    }

    public X509Certificate buildSelfSigned()
            throws GeneralSecurityException
    {
        checkState(publicKey != null, "publicKey is not set");
        checkState(privateKey != null, "privateKey is not set");
        checkState(issuer != null, "issuer is not set");
        checkState(notBefore != null, "notBefore is not set");
        checkState(notAfter != null, "notAfter is not set");
        checkState(!notBefore.isAfter(notAfter), "notAfter is before notBefore");
        checkState(subject != null, "subject is not set");

        byte[] publicKeyHash = hashPublicKey();

        List<byte[]> sans = new ArrayList<>();
        sanDnsNames.stream()
                .map(address -> encodeContextSpecificTag(2, address.getBytes(UTF_8)))
                .forEach(sans::add);
        sanIpAddresses.stream()
                .map(InetAddress::getAddress)
                .map(address -> encodeContextSpecificTag(7, address))
                .forEach(sans::add);

        byte[] rawCertificate = encodeSequence(
                // version: 2
                encodeContextSpecificSequence(0, encodeInteger(2)),
                // serialNumber
                encodeInteger(serialNumber),
                // signature kind
                encodeSequence(
                        SHA_256_WITH_RSA_ENCRYPTION_OID,
                        encodeNull()),
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
                encodeContextSpecificSequence(3, encodeSequence(
                        encodeSequence(
                                SUBJECT_KEY_IDENTIFIER_OID,
                                encodeOctetString(encodeOctetString(publicKeyHash))),
                        encodeSequence(
                                AUTHORITY_KEY_IDENTIFIER_OID,
                                encodeOctetString(encodeSequence(encodeContextSpecificTag(0, publicKeyHash)))),
                        encodeSequence(
                                BASIC_CONSTRAINTS_OID,
                                encodeBooleanTrue(),
                                encodeOctetString(encodeSequence(encodeBooleanTrue()))),
                        encodeSequence(
                                SUBJECT_ALT_NAME_OID,
                                encodeOctetString(
                                        encodeSequence(sans.toArray(new byte[0][])))))));

        byte[] signature = signCertificate(rawCertificate);

        byte[] encodedCertificate = encodeSequence(
                rawCertificate,
                // signature kind
                encodeSequence(
                        SHA_256_WITH_RSA_ENCRYPTION_OID,
                        encodeNull()),
                // signature
                DerUtils.encodeBitString(0, signature));

        CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
        return (X509Certificate) certificateFactory.generateCertificate(new ByteArrayInputStream(encodedCertificate));
    }

    private byte[] signCertificate(byte[] rawCertificate)
            throws GeneralSecurityException
    {
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(privateKey);
        signature.update(rawCertificate);
        byte[] digitalSignature = signature.sign();
        return digitalSignature;
    }

    private byte[] hashPublicKey()
            throws NoSuchAlgorithmException
    {
        byte[] rawKey = encodeSequence(
                encodeInteger(publicKey.getModulus()),
                encodeInteger(publicKey.getPublicExponent()));
        MessageDigest digest = MessageDigest.getInstance("SHA-1");
        return digest.digest(rawKey);
    }
}
