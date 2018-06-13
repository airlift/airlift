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

import com.google.common.collect.ImmutableSet;

import javax.crypto.Cipher;
import javax.crypto.EncryptedPrivateKeyInfo;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.security.auth.x500.X500Principal;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.DSAKey;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.google.common.io.Files.asCharSource;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.util.Base64.getMimeDecoder;
import static java.util.regex.Pattern.CASE_INSENSITIVE;
import static javax.crypto.Cipher.DECRYPT_MODE;

public final class PemReader
{
    private static final Pattern CERT_PATTERN = Pattern.compile(
            "-+BEGIN\\s+.*CERTIFICATE[^-]*-+(?:\\s|\\r|\\n)+" + // Header
                    "([a-z0-9+/=\\r\\n]+)" +                    // Base64 text
                    "-+END\\s+.*CERTIFICATE[^-]*-+",            // Footer
            CASE_INSENSITIVE);

    private static final Pattern PRIVATE_KEY_PATTERN = Pattern.compile(
            "-+BEGIN\\s+.*PRIVATE\\s+KEY[^-]*-+(?:\\s|\\r|\\n)+" + // Header
                    "([a-z0-9+/=\\r\\n]+)" +                       // Base64 text
                    "-+END\\s+.*PRIVATE\\s+KEY[^-]*-+",            // Footer
            CASE_INSENSITIVE);

    private static final Pattern PUBLIC_KEY_PATTERN = Pattern.compile(
            "-+BEGIN\\s+.*PUBLIC\\s+KEY[^-]*-+(?:\\s|\\r|\\n)+" + // Header
                    "([a-z0-9+/=\\r\\n]+)" +                      // Base64 text
                    "-+END\\s+.*PUBLIC\\s+KEY[^-]*-+",            // Footer
            CASE_INSENSITIVE);

    private static final byte[] TEST_SIGNATURE_DATA = "TEST_SIGNATURE_DATA".getBytes(US_ASCII);
    private static final Set<String> SUPPORTED_KEY_TYPES = ImmutableSet.of("RSA", "EC", "DSA");

    private PemReader() {}

    public static boolean isPem(File file)
            throws IOException
    {
        return isPem(asCharSource(file, US_ASCII).read());
    }

    public static boolean isPem(String data)
    {
        return CERT_PATTERN.matcher(data).find() ||
                PUBLIC_KEY_PATTERN.matcher(data).find() ||
                PRIVATE_KEY_PATTERN.matcher(data).find();
    }

    public static KeyStore loadTrustStore(File certificateChainFile)
            throws IOException, GeneralSecurityException
    {
        KeyStore keyStore = KeyStore.getInstance("JKS");
        keyStore.load(null, null);

        List<X509Certificate> certificateChain = readCertificateChain(certificateChainFile);
        for (X509Certificate certificate : certificateChain) {
            X500Principal principal = certificate.getSubjectX500Principal();
            keyStore.setCertificateEntry(principal.getName("RFC2253"), certificate);
        }
        return keyStore;
    }

    public static KeyStore loadKeyStore(File certificateChainFile, File privateKeyFile, Optional<String> keyPassword)
            throws IOException, GeneralSecurityException
    {
        PrivateKey key = loadPrivateKey(privateKeyFile, keyPassword);

        List<X509Certificate> certificateChain = readCertificateChain(certificateChainFile);
        if (certificateChain.isEmpty()) {
            throw new CertificateException("Certificate file does not contain any certificates: " + certificateChainFile);
        }

        KeyStore keyStore = KeyStore.getInstance("JKS");
        keyStore.load(null, null);

        // ensure there is a certificate that matches the private key
        Certificate[] certificates = certificateChain.toArray(new Certificate[0]);
        boolean foundMatchingCertificate = false;
        for (int i = 0; i < certificates.length; i++) {
            Certificate certificate = certificates[i];
            if (matches(key, certificate)) {
                foundMatchingCertificate = true;
                // certificate for private key must be in index zero
                certificates[i] = certificates[0];
                certificates[0] = certificate;
                break;
            }
        }
        if (!foundMatchingCertificate) {
            throw new KeyStoreException("Private key does not match the public key of any certificate");
        }

        keyStore.setKeyEntry("key", key, new char[0], certificates);
        return keyStore;
    }

    public static List<X509Certificate> readCertificateChain(File certificateChainFile)
            throws IOException, GeneralSecurityException
    {
        String contents = asCharSource(certificateChainFile, US_ASCII).read();
        return readCertificateChain(contents);
    }

    public static List<X509Certificate> readCertificateChain(String certificateChain)
            throws CertificateException
    {
        Matcher matcher = CERT_PATTERN.matcher(certificateChain);
        CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
        List<X509Certificate> certificates = new ArrayList<>();

        int start = 0;
        while (matcher.find(start)) {
            byte[] buffer = base64Decode(matcher.group(1));
            certificates.add((X509Certificate) certificateFactory.generateCertificate(new ByteArrayInputStream(buffer)));
            start = matcher.end();
        }

        return certificates;
    }

    public static PrivateKey loadPrivateKey(File privateKeyFile, Optional<String> keyPassword)
            throws IOException, GeneralSecurityException
    {
        String privateKey = asCharSource(privateKeyFile, US_ASCII).read();
        return loadPrivateKey(privateKey, keyPassword);
    }

    public static PrivateKey loadPrivateKey(String privateKey, Optional<String> keyPassword)
            throws IOException, GeneralSecurityException
    {
        Matcher matcher = PRIVATE_KEY_PATTERN.matcher(privateKey);
        if (!matcher.find()) {
            throw new KeyStoreException("did not find a private key");
        }
        byte[] encodedKey = base64Decode(matcher.group(1));

        PKCS8EncodedKeySpec encodedKeySpec;
        if (keyPassword.isPresent()) {
            EncryptedPrivateKeyInfo encryptedPrivateKeyInfo = new EncryptedPrivateKeyInfo(encodedKey);
            SecretKeyFactory keyFactory = SecretKeyFactory.getInstance(encryptedPrivateKeyInfo.getAlgName());
            SecretKey secretKey = keyFactory.generateSecret(new PBEKeySpec(keyPassword.get().toCharArray()));

            Cipher cipher = Cipher.getInstance(encryptedPrivateKeyInfo.getAlgName());
            cipher.init(DECRYPT_MODE, secretKey, encryptedPrivateKeyInfo.getAlgParameters());

            encodedKeySpec = encryptedPrivateKeyInfo.getKeySpec(cipher);
        }
        else {
            encodedKeySpec = new PKCS8EncodedKeySpec(encodedKey);
        }

        // this code requires a key in PKCS8 format which is not the default openssl format
        // to convert to the PKCS8 format you use : openssl pkcs8 -topk8 ...
        Set<String> algorithms = ImmutableSet.of("RSA", "EC", "DSA");
        for (String algorithm : algorithms) {
            try {
                KeyFactory keyFactory = KeyFactory.getInstance(algorithm);
                return keyFactory.generatePrivate(encodedKeySpec);
            }
            catch (InvalidKeySpecException ignore) {
            }
        }
        throw new InvalidKeySpecException("Key type must be one of " + algorithms);
    }

    public static PublicKey loadPublicKey(File publicKeyFile)
            throws IOException, GeneralSecurityException
    {
        String publicKey = asCharSource(publicKeyFile, US_ASCII).read();
        return loadPublicKey(publicKey);
    }

    public static PublicKey loadPublicKey(String publicKey)
            throws GeneralSecurityException
    {
        Matcher matcher = PUBLIC_KEY_PATTERN.matcher(publicKey);
        if (!matcher.find()) {
            throw new KeyStoreException("did not find a public key");
        }
        String data = matcher.group(1);
        byte[] encodedKey = base64Decode(data);

        X509EncodedKeySpec encodedKeySpec = new X509EncodedKeySpec(encodedKey);

        for (String algorithm : SUPPORTED_KEY_TYPES) {
            try {
                KeyFactory keyFactory = KeyFactory.getInstance(algorithm);
                return keyFactory.generatePublic(encodedKeySpec);
            }
            catch (InvalidKeySpecException ignore) {
            }
        }
        throw new InvalidKeySpecException("Key type must be one of " + SUPPORTED_KEY_TYPES);
    }

    private static boolean matches(PrivateKey privateKey, Certificate certificate)
    {
        try {
            PublicKey publicKey = certificate.getPublicKey();

            Signature signer = createSignature(privateKey, publicKey);

            signer.initSign(privateKey);
            signer.update(TEST_SIGNATURE_DATA);
            byte[] signature = signer.sign();

            signer.initVerify(publicKey);
            signer.update(TEST_SIGNATURE_DATA);
            return signer.verify(signature);
        }
        catch (GeneralSecurityException ignored) {
            return false;
        }
    }

    private static Signature createSignature(PrivateKey privateKey, PublicKey publicKey)
            throws GeneralSecurityException
    {
        if (privateKey instanceof RSAPrivateKey && publicKey instanceof RSAPublicKey) {
            return Signature.getInstance("SHA1withRSA");
        }
        if (privateKey instanceof ECPrivateKey && publicKey instanceof ECPublicKey) {
            return Signature.getInstance("SHA1withECDSA");
        }
        if (privateKey instanceof DSAKey && publicKey instanceof DSAKey) {
            return Signature.getInstance("SHA1withDSA");
        }
        throw new InvalidKeySpecException("Key type must be one of " + SUPPORTED_KEY_TYPES);
    }

    private static byte[] base64Decode(String base64)
    {
        return getMimeDecoder().decode(base64.getBytes(US_ASCII));
    }
}
