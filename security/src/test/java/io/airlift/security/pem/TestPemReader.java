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

import org.testng.annotations.Test;

import javax.naming.InvalidNameException;
import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;

import java.io.File;
import java.net.URL;
import java.security.Key;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Optional;

import static com.google.common.collect.Iterables.getOnlyElement;
import static io.airlift.security.pem.PemReader.loadKeyStore;
import static io.airlift.security.pem.PemReader.loadPrivateKey;
import static io.airlift.security.pem.PemReader.loadPublicKey;
import static io.airlift.security.pem.PemReader.loadTrustStore;
import static io.airlift.security.pem.PemReader.readCertificateChain;
import static io.airlift.security.pem.PemWriter.writeCertificate;
import static io.airlift.security.pem.PemWriter.writePrivateKey;
import static io.airlift.security.pem.PemWriter.writePublicKey;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

public class TestPemReader
{
    @Test
    public void testLoadKeyStore()
            throws Exception
    {
        testLoadKeyStore("rsa.crt", "rsa.key");
        testLoadKeyStore("ec.crt", "ec.key");
        testLoadKeyStore("dsa.crt", "dsa.key");
    }

    private static void testLoadKeyStore(String certFile, String keyFile)
            throws Exception
    {
        KeyStore keyStore = loadKeyStore(getResourceFile(certFile), getResourceFile(keyFile), Optional.empty());
        assertCertificateChain(keyStore);
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
        assertCertificateChain(loadTrustStore(getResourceFile("rsa.crt")));
        assertCertificateChain(loadTrustStore(getResourceFile("ec.crt")));
        assertCertificateChain(loadTrustStore(getResourceFile("dsa.crt")));
    }

    @Test
    public void testLoadPublicKey()
            throws Exception
    {
        testLoadPublicKey("rsa.crt", "rsa.pub");
        testLoadPublicKey("ec.crt", "ec.pub");
        testLoadPublicKey("dsa.crt", "dsa.pub");
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

    private static void assertCertificateChain(KeyStore keyStore)
            throws Exception
    {
        ArrayList<String> aliases = Collections.list(keyStore.aliases());
        assertEquals(aliases.size(), 1);
        Certificate certificate = keyStore.getCertificate(aliases.get(0));
        assertNotNull(certificate);

        assertTrue(certificate instanceof X509Certificate);
        X509Certificate x509Certificate = (X509Certificate) certificate;

        assertX509Certificate(x509Certificate);

        X509Certificate certificateCopy = getOnlyElement(readCertificateChain(writeCertificate(x509Certificate)));
        assertX509Certificate(certificateCopy);
    }

    private static void assertX509Certificate(X509Certificate x509Certificate)
            throws InvalidNameException
    {
        LdapName ldapName = new LdapName(x509Certificate.getSubjectX500Principal().getName());
        String cn = ldapName.getRdns().stream()
                .filter(rdn -> rdn.getType().equals("CN"))
                .map(Rdn::getValue)
                .findFirst()
                .map(String.class::cast)
                .orElseThrow(() -> new AssertionError("Certificate subject name does not contain a CN"));
        assertEquals(cn, "Test User");
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
