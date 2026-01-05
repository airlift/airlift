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
}
