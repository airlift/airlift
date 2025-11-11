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
package io.airlift.security.pem;

import org.junit.jupiter.api.Test;

import javax.naming.InvalidNameException;
import javax.naming.ldap.LdapName;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.security.Key;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Optional;
import java.util.regex.Matcher;

import static com.google.common.collect.MoreCollectors.onlyElement;
import static com.google.common.io.Files.asCharSource;
import static io.airlift.security.pem.PemReader.PRIVATE_KEY_PATTERN;
import static io.airlift.security.pem.PemReader.PUBLIC_KEY_PATTERN;
import static io.airlift.security.pem.PemReader.dsaPkcs1ToPkcs8;
import static io.airlift.security.pem.PemReader.ecPkcs1ToPkcs8;
import static io.airlift.security.pem.PemReader.isPem;
import static io.airlift.security.pem.PemReader.loadKeyStore;
import static io.airlift.security.pem.PemReader.loadPrivateKey;
import static io.airlift.security.pem.PemReader.loadPublicKey;
import static io.airlift.security.pem.PemReader.loadTrustStore;
import static io.airlift.security.pem.PemReader.readCertificateChain;
import static io.airlift.security.pem.PemReader.rsaPkcs1ToPkcs8;
import static io.airlift.security.pem.PemReader.rsaPublicKeyPkcs1ToPkcs8;
import static io.airlift.security.pem.PemWriter.writeCertificate;
import static io.airlift.security.pem.PemWriter.writePrivateKey;
import static io.airlift.security.pem.PemWriter.writePublicKey;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.assertj.core.api.Assertions.assertThat;

public class TestPemReader
{
    private static final String CA_NAME = "OU=RootCA,O=Airlift,L=Palo Alto,ST=CA,C=US";
    private static final String CLIENT_NAME = "CN=Test User,OU=Server,O=Airlift,L=Palo Alto,ST=CA,C=US";

    private static final Optional<String> NO_PASSWORD = Optional.empty();
    private static final Optional<String> KEY_PASSWORD = Optional.of("airlift");

    @Test
    public void testLoadKeyStore()
            throws Exception
    {
        testLoadKeyStore("rsa.client.crt", "rsa.client.pkcs8.key", NO_PASSWORD, CLIENT_NAME);
        testLoadKeyStore("ec.client.crt", "ec.client.pkcs8.key", NO_PASSWORD, CLIENT_NAME);
        testLoadKeyStore("dsa.client.crt", "dsa.client.pkcs8.key", NO_PASSWORD, CLIENT_NAME);

        testLoadKeyStore("rsa.client.crt", "rsa.client.pkcs8.key.encrypted", KEY_PASSWORD, CLIENT_NAME);
        testLoadKeyStore("ec.client.crt", "ec.client.pkcs8.key.encrypted", KEY_PASSWORD, CLIENT_NAME);
        testLoadKeyStore("dsa.client.crt", "dsa.client.pkcs8.key.encrypted", KEY_PASSWORD, CLIENT_NAME);

        testLoadKeyStore("rsa.client.pkcs8.pem.encrypted", "rsa.client.pkcs8.pem.encrypted", KEY_PASSWORD, CLIENT_NAME);
        testLoadKeyStore("dsa.client.pkcs8.pem.encrypted", "dsa.client.pkcs8.pem.encrypted", KEY_PASSWORD, CLIENT_NAME);
        testLoadKeyStore("ec.client.pkcs8.pem.encrypted", "ec.client.pkcs8.pem.encrypted", KEY_PASSWORD, CLIENT_NAME);

        testLoadKeyStore("rsa.client.crt", "rsa.client.pkcs1.key", NO_PASSWORD, CLIENT_NAME);
        testLoadKeyStore("ec.client.crt", "ec.client.pkcs1.key", NO_PASSWORD, CLIENT_NAME);
        testLoadKeyStore("dsa.client.crt", "dsa.client.pkcs1.key", NO_PASSWORD, CLIENT_NAME);

        testLoadKeyStore("rsa.client.pkcs8.pem.encrypted", "rsa.client.pkcs1.pem", NO_PASSWORD, CLIENT_NAME);
        testLoadKeyStore("dsa.client.pkcs8.pem.encrypted", "dsa.client.pkcs1.pem", NO_PASSWORD, CLIENT_NAME);
        testLoadKeyStore("ec.client.pkcs8.pem.encrypted", "ec.client.pkcs1.pem", NO_PASSWORD, CLIENT_NAME);
    }

    private static void testLoadKeyStore(String certFile, String keyFile, Optional<String> keyPassword, String expectedName)
            throws Exception
    {
        KeyStore keyStore = loadKeyStore(getResourceFile(certFile), getResourceFile(keyFile), keyPassword);
        assertCertificateChain(keyStore, expectedName);
        assertThat(keyStore.getCertificate("key")).isNotNull();

        Key key = keyStore.getKey("key", new char[0]);
        assertThat(key).isNotNull();
        assertThat(key).isInstanceOf(PrivateKey.class);
        PrivateKey privateKey = (PrivateKey) key;
        String encodedPrivateKey = writePrivateKey(privateKey);
        assertThat(key).isEqualTo(loadPrivateKey(encodedPrivateKey, Optional.empty()));
    }

    @Test
    public void testLoadTrustStore()
            throws Exception
    {
        assertCertificateChain(loadTrustStore(getResourceFile("rsa.ca.crt")), CA_NAME);
        assertCertificateChain(loadTrustStore(getResourceFile("ec.ca.crt")), CA_NAME);
        assertCertificateChain(loadTrustStore(getResourceFile("dsa.ca.crt")), CA_NAME);
    }

    @Test
    public void testLoadPublicKey()
            throws Exception
    {
        testLoadPublicKey("rsa.client.crt", "rsa.client.pkcs8.pub");
        testLoadPublicKey("rsa.client.crt", "rsa.client.pkcs1.pub");
        testLoadPublicKey("ec.client.crt", "ec.client.pkcs8.pub");
        testLoadPublicKey("dsa.client.crt", "dsa.client.pkcs8.pub");
    }

    private static void testLoadPublicKey(String certFile, String keyFile)
            throws Exception
    {
        File file = getResourceFile(keyFile);
        assertThat(isPem(file)).isTrue();
        PublicKey publicKey = loadPublicKey(file);
        assertThat(publicKey).isNotNull();
        X509Certificate certificate = readCertificateChain(getResourceFile(certFile)).stream().collect(onlyElement());
        assertThat(publicKey).isEqualTo(certificate.getPublicKey());

        String encodedPrivateKey = writePublicKey(publicKey);
        assertThat(publicKey).isEqualTo(loadPublicKey(encodedPrivateKey));
    }

    @Test
    public void testRsaPublicKeyPkcs1ToPkcs8()
            throws Exception
    {
        byte[] pkcs8 = loadPublicKeyData("rsa.client.pkcs8.pub");
        byte[] pkcs1 = loadPublicKeyData("rsa.client.pkcs1.pub");
        assertThat(rsaPublicKeyPkcs1ToPkcs8(pkcs1)).isEqualTo(pkcs8);
    }

    @Test
    public void testRsaPkcs1ToPkcs8()
            throws Exception
    {
        byte[] pkcs8 = loadPrivateKeyData("rsa.client.pkcs8.key");
        byte[] pkcs1 = loadPrivateKeyData("rsa.client.pkcs1.key");
        assertThat(rsaPkcs1ToPkcs8(pkcs1)).isEqualTo(pkcs8);
    }

    @Test
    public void testDsaPkcs1ToPkcs8()
            throws Exception
    {
        byte[] pkcs8 = loadPrivateKeyData("dsa.client.pkcs8.key");
        byte[] pkcs1 = loadPrivateKeyData("dsa.client.pkcs1.key");
        assertThat(dsaPkcs1ToPkcs8(pkcs1)).isEqualTo(pkcs8);
    }

    @Test
    public void testEcPkcs1ToPkcs8()
            throws Exception
    {
        byte[] pkcs8 = loadPrivateKeyData("ec.client.pkcs8.key");
        byte[] pkcs1 = loadPrivateKeyData("ec.client.pkcs1.key");
        assertThat(ecPkcs1ToPkcs8(pkcs1)).isEqualTo(pkcs8);
    }

    private static void assertCertificateChain(KeyStore keyStore, String expectedName)
            throws Exception
    {
        ArrayList<String> aliases = Collections.list(keyStore.aliases());
        assertThat(aliases.size()).isEqualTo(1);
        Certificate certificate = keyStore.getCertificate(aliases.getFirst());
        assertThat(certificate).isNotNull();

        assertThat(certificate).isInstanceOf(X509Certificate.class);
        X509Certificate x509Certificate = (X509Certificate) certificate;

        assertX509Certificate(x509Certificate, expectedName);

        X509Certificate certificateCopy = readCertificateChain(writeCertificate(x509Certificate)).stream().collect(onlyElement());
        assertX509Certificate(certificateCopy, expectedName);
    }

    private static void assertX509Certificate(X509Certificate x509Certificate, String expectedName)
            throws InvalidNameException
    {
        LdapName ldapName = new LdapName(x509Certificate.getSubjectX500Principal().getName());
        assertThat(ldapName.toString()).isEqualTo(expectedName);
    }

    private static byte[] loadPrivateKeyData(String keyFile)
            throws IOException, KeyStoreException
    {
        File file = getResourceFile(keyFile);
        assertThat(isPem(file)).isTrue();
        String privateKey = asCharSource(file, US_ASCII).read();
        Matcher matcher = PRIVATE_KEY_PATTERN.matcher(privateKey);
        if (!matcher.find()) {
            throw new KeyStoreException("did not find a private key");
        }
        return PemReader.base64Decode(matcher.group(2));
    }

    private static byte[] loadPublicKeyData(String keyFile)
            throws IOException, KeyStoreException
    {
        File file = getResourceFile(keyFile);
        assertThat(isPem(file)).isTrue();
        String privateKey = asCharSource(file, US_ASCII).read();
        Matcher matcher = PUBLIC_KEY_PATTERN.matcher(privateKey);
        if (!matcher.find()) {
            throw new KeyStoreException("did not find a private key");
        }
        return PemReader.base64Decode(matcher.group(2));
    }

    private static File getResourceFile(String name)
    {
        URL resource = TestPemReader.class.getClassLoader().getResource(name);
        if (resource == null) {
            throw new IllegalArgumentException("Resource not found " + name);
        }
        return new File(resource.getFile());
    }
}
