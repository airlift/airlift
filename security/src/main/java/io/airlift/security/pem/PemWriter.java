package io.airlift.security.pem;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;

import static java.util.Base64.getMimeEncoder;

public final class PemWriter
{
    private PemWriter() {}

    public static String writePublicKey(PublicKey publicKey)
    {
        return encodePem("PUBLIC KEY", publicKey.getEncoded());
    }

    public static String writePrivateKey(PrivateKey privateKey)
    {
        return encodePem("PRIVATE KEY", privateKey.getEncoded());
    }

    public static String writeCertificate(X509Certificate certificate)
            throws CertificateEncodingException
    {
        return encodePem("CERTIFICATE", certificate.getEncoded());
    }

    private static String encodePem(String type, byte[] encoded)
    {
        return "-----BEGIN " + type + "-----\n" +
                getMimeEncoder(64, new byte[] {'\n'}).encodeToString(encoded) + '\n' +
                "-----END " + type + "-----\n";
    }
}
