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
package com.facebook.airlift.security.pem;

import org.testng.annotations.Test;

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

import static com.google.common.collect.Iterables.getOnlyElement;
import static com.google.common.io.Files.asCharSource;
import static com.facebook.airlift.security.pem.PemReader.PRIVATE_KEY_PATTERN;
import static com.facebook.airlift.security.pem.PemReader.dsaPkcs1ToPkcs8;
import static com.facebook.airlift.security.pem.PemReader.ecPkcs1ToPkcs8;
import static com.facebook.airlift.security.pem.PemReader.loadKeyStore;
import static com.facebook.airlift.security.pem.PemReader.loadPrivateKey;
import static com.facebook.airlift.security.pem.PemReader.loadPublicKey;
import static com.facebook.airlift.security.pem.PemReader.loadTrustStore;
import static com.facebook.airlift.security.pem.PemReader.readCertificateChain;
import static com.facebook.airlift.security.pem.PemReader.rsaPkcs1ToPkcs8;
import static com.facebook.airlift.security.pem.PemWriter.writeCertificate;
import static com.facebook.airlift.security.pem.PemWriter.writePrivateKey;
import static com.facebook.airlift.security.pem.PemWriter.writePublicKey;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

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
        assertNotNull(keyStore.getCertificate("key"));

        Key key = keyStore.getKey("key", new char[0]);
        assertNotNull(key);
        assertTrue(key instanceof PrivateKey);
        PrivateKey privateKey = (PrivateKey) key;
        String encodedPrivateKey = writePrivateKey(privateKey);
        assertEquals(key, loadPrivateKey(encodedPrivateKey, Optional.empty()));
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
        testLoadPublicKey("ec.client.crt", "ec.client.pkcs8.pub");
        testLoadPublicKey("dsa.client.crt", "dsa.client.pkcs8.pub");
    }

    private static void testLoadPublicKey(String certFile, String keyFile)
            throws Exception
    {
        PublicKey publicKey = loadPublicKey(getResourceFile(keyFile));
        assertNotNull(publicKey);
        X509Certificate certificate = getOnlyElement(readCertificateChain(getResourceFile(certFile)));
        assertEquals(publicKey, certificate.getPublicKey());

        String encodedPrivateKey = writePublicKey(publicKey);
        assertEquals(publicKey, loadPublicKey(encodedPrivateKey));
    }

    @Test
    public void testRsaPkcs1ToPkcs8()
            throws Exception
    {
        byte[] pkcs8 = loadPrivateKeyData("rsa.client.pkcs8.key");
        byte[] pkcs1 = loadPrivateKeyData("rsa.client.pkcs1.key");
        assertEquals(rsaPkcs1ToPkcs8(pkcs1), pkcs8);
    }

    @Test
    public void testDsaPkcs1ToPkcs8()
            throws Exception
    {
        byte[] pkcs8 = loadPrivateKeyData("dsa.client.pkcs8.key");
        byte[] pkcs1 = loadPrivateKeyData("dsa.client.pkcs1.key");
        assertEquals(dsaPkcs1ToPkcs8(pkcs1), pkcs8);
    }

    @Test
    public void testEcPkcs1ToPkcs8()
            throws Exception
    {
        byte[] pkcs8 = loadPrivateKeyData("ec.client.pkcs8.key");
        byte[] pkcs1 = loadPrivateKeyData("ec.client.pkcs1.key");
        assertEquals(ecPkcs1ToPkcs8(pkcs1), pkcs8);
    }

    private static void assertCertificateChain(KeyStore keyStore, String expectedName)
            throws Exception
    {
        ArrayList<String> aliases = Collections.list(keyStore.aliases());
        assertEquals(aliases.size(), 1);
        Certificate certificate = keyStore.getCertificate(aliases.get(0));
        assertNotNull(certificate);

        assertTrue(certificate instanceof X509Certificate);
        X509Certificate x509Certificate = (X509Certificate) certificate;

        assertX509Certificate(x509Certificate, expectedName);

        X509Certificate certificateCopy = getOnlyElement(readCertificateChain(writeCertificate(x509Certificate)));
        assertX509Certificate(certificateCopy, expectedName);
    }

    private static void assertX509Certificate(X509Certificate x509Certificate, String expectedName)
            throws InvalidNameException
    {
        LdapName ldapName = new LdapName(x509Certificate.getSubjectX500Principal().getName());
        assertEquals(ldapName.toString(), expectedName);
    }

    private static byte[] loadPrivateKeyData(String keyFile)
            throws IOException, KeyStoreException
    {
        String privateKey = asCharSource(getResourceFile(keyFile), US_ASCII).read();
        Matcher matcher = PRIVATE_KEY_PATTERN.matcher(privateKey);
        if (!matcher.find()) {
            throw new KeyStoreException("did not find a private key");
        }
        byte[] data = PemReader.base64Decode(matcher.group(2));
        return data;
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
