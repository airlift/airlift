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

import org.bouncycastle.asn1.x509.AuthorityKeyIdentifier;
import org.bouncycastle.asn1.x509.Certificate;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.SubjectKeyIdentifier;
import org.junit.jupiter.api.Test;

import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import javax.security.auth.x500.X500Principal;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.security.spec.ECGenParameterSpec;
import java.time.LocalDate;
import java.time.ZoneId;

import static io.airlift.security.cert.CertificateBuilder.certificateBuilder;
import static java.time.ZoneOffset.UTC;
import static java.time.temporal.ChronoUnit.YEARS;
import static org.assertj.core.api.Assertions.assertThat;

public class TestCertificateBuilder
{
    @Test
    public void test()
            throws Exception
    {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair keyPair = generator.generateKeyPair();

        X500Principal issuer = new X500Principal("CN=issuer,O=Airlift");
        X500Principal subject = new X500Principal("CN=subject,O=Airlift");
        LocalDate notBefore = LocalDate.now(ZoneId.systemDefault());
        LocalDate notAfter = notBefore.plus(10, YEARS);
        X509Certificate certificate = certificateBuilder()
                .setKeyPair(keyPair)
                .setSerialNumber(12345)
                .setIssuer(issuer)
                .setNotBefore(notBefore)
                .setNotAfter(notAfter)
                .setSubject(subject)
                .buildSelfSigned();

        assertThat(certificate.getSerialNumber()).isEqualTo(BigInteger.valueOf(12345));
        assertThat(certificate.getIssuerX500Principal()).isEqualTo(issuer);
        assertThat(certificate.getNotBefore().toInstant()).isEqualTo(notBefore.atStartOfDay().toInstant(UTC));
        assertThat(certificate.getNotAfter().toInstant()).isEqualTo(notAfter.atTime(23, 59, 59).toInstant(UTC));
        assertThat(certificate.getSubjectX500Principal()).isEqualTo(subject);
        assertThat(certificate.getPublicKey()).isEqualTo(keyPair.getPublic());

        // verify certificate trusts itself
        KeyStore keyStore = KeyStore.getInstance("JKS");
        keyStore.load(null, new char[0]);
        keyStore.setCertificateEntry("test", certificate);
        TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManagerFactory.init(keyStore);

        for (TrustManager trustManager : trustManagerFactory.getTrustManagers()) {
            if (trustManager instanceof X509TrustManager) {
                X509TrustManager x509TrustManager = (X509TrustManager) trustManager;
                x509TrustManager.checkServerTrusted(new X509Certificate[] {certificate}, "EC");
            }
        }
    }

    @Test
    public void testEcLeafIssuedByEd25519Ca()
            throws Exception
    {
        KeyPair caKeyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        X500Principal caSubject = new X500Principal("CN=Ed25519 CA");
        X509Certificate caCertificate = newCertificateBuilder(caKeyPair, caSubject)
                .buildSelfSigned();
        caCertificate.verify(caKeyPair.getPublic());

        KeyPairGenerator leafGenerator = KeyPairGenerator.getInstance("EC");
        leafGenerator.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair leafKeyPair = leafGenerator.generateKeyPair();
        X509Certificate leafCertificate = newCertificateBuilder(leafKeyPair, new X500Principal("CN=leaf"))
                .setSerialNumber(2)
                .setIssuer(caSubject)
                .setCertificateAuthority(false)
                .addSanDnsName("localhost")
                .buildIssuedBy(caKeyPair.getPrivate(), caKeyPair.getPublic());
        leafCertificate.verify(caKeyPair.getPublic());
        assertThat(leafCertificate.getPublicKey()).isEqualTo(leafKeyPair.getPublic());
        assertThat(leafCertificate.getBasicConstraints()).isEqualTo(-1);

        Certificate ca = Certificate.getInstance(caCertificate.getEncoded());
        Certificate leaf = Certificate.getInstance(leafCertificate.getEncoded());
        for (Certificate certificate : new Certificate[] {ca, leaf}) {
            assertThat(certificate.getSignatureAlgorithm().getAlgorithm().getId()).isEqualTo("1.3.101.112");
            assertThat(certificate.getSignatureAlgorithm().getParameters()).isNull();
            assertThat(certificate.getTBSCertificate().getSignature()).isEqualTo(certificate.getSignatureAlgorithm());
        }
        byte[] caKeyIdentifier = SubjectKeyIdentifier.getInstance(ca.getTBSCertificate().getExtensions()
                .getExtensionParsedValue(Extension.subjectKeyIdentifier)).getKeyIdentifier();
        byte[] leafAuthorityKeyIdentifier = AuthorityKeyIdentifier.getInstance(leaf.getTBSCertificate().getExtensions()
                .getExtensionParsedValue(Extension.authorityKeyIdentifier)).getKeyIdentifierOctets();
        assertThat(leafAuthorityKeyIdentifier).isEqualTo(caKeyIdentifier);

        KeyStore trustStore = KeyStore.getInstance("JKS");
        trustStore.load(null, null);
        trustStore.setCertificateEntry("ca", caCertificate);
        TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManagerFactory.init(trustStore);
        for (TrustManager trustManager : trustManagerFactory.getTrustManagers()) {
            if (trustManager instanceof X509TrustManager x509TrustManager) {
                x509TrustManager.checkServerTrusted(new X509Certificate[] {leafCertificate, caCertificate}, "ECDHE_ECDSA");
            }
        }
    }

    private static CertificateBuilder newCertificateBuilder(KeyPair keyPair, X500Principal subject)
    {
        LocalDate today = LocalDate.now(UTC);
        return certificateBuilder()
                .setKeyPair(keyPair)
                .setSerialNumber(1)
                .setIssuer(subject)
                .setSubject(subject)
                .setNotBefore(today.minusDays(1))
                .setNotAfter(today.plusYears(1));
    }
}
