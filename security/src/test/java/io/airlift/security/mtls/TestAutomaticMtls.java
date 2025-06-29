package io.airlift.security.mtls;

import org.junit.jupiter.api.Test;

import java.security.GeneralSecurityException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestAutomaticMtls
{
    @Test
    public void testTrustManager()
            throws GeneralSecurityException
    {
        X509Certificate certificate = AutomaticMtls.certificateBuilder("sharedSecret", "commonName")
                .addSanDnsNames(List.of("localhost"))
                .buildSelfSigned();

        // This should pass as the certificate is self-signed and matches the trust manager's expectations
        checkCertificate(certificate, "sharedSecret", "commonName");

        assertThatThrownBy(() -> checkCertificate(certificate, "wrongSharedSecret", "commonName"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Peer cert public key doesn't match trusted key");

        assertThatThrownBy(() -> checkCertificate(certificate, "sharedSecret", "wrongCommonName"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Peer certificate subject 'CN=commonName' does not match expected subject: 'CN=wrongCommonName'");
    }

    private void checkCertificate(X509Certificate certificate, String sharedSecret, String commonName)
            throws CertificateException
    {
        AutomaticMtls.createTrustManager(sharedSecret, commonName).checkClientTrusted(new X509Certificate[] {certificate}, "ECDSA");
    }
}
