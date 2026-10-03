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
package io.airlift.secrets.keystore;

import io.airlift.testing.TempFile;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.Locale;
import java.util.Map;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

@TestInstance(PER_CLASS)
final class TestKeystoreSecretProvider
{
    private TempFile keystoreFile;

    private KeystoreSecretProvider secretProvider;

    @BeforeAll
    public void setup()
            throws Exception
    {
        keystoreFile = new TempFile();

        char[] password = "password".toCharArray();
        KeyStore keystore = KeyStore.getInstance("pkcs12");
        keystore.load(null, password);

        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBE");

        keystore.setEntry(
                "key",
                new KeyStore.SecretKeyEntry(factory.generateSecret(new PBEKeySpec("value".toCharArray()))),
                new KeyStore.PasswordProtection(password));

        try (FileOutputStream outputStream = new FileOutputStream(keystoreFile.file())) {
            keystore.store(outputStream, password);
        }

        secretProvider = new KeystoreSecretProvider(new KeystoreSecretProviderConfig()
                .setKeyStoreType("jks")
                .setKeyStoreFilePath(keystoreFile.file().getAbsolutePath())
                .setKeyStorePassword("password"));
    }

    @AfterAll
    public void teardown()
    {
        if (keystoreFile != null) {
            keystoreFile.close();
        }
    }

    @Test
    public void testPbeSecretKeyEntryFormat()
    {
        assertThat(secretProvider.resolveSecretValue("key")).isEqualTo("value");
    }

    @Test
    public void testConfigurationResolverWithInvalidKey()
    {
        assertThatThrownBy(() -> secretProvider.resolveSecretValue("invalid_key"))
                .hasMessageContaining("Key not found in keystore: invalid_key");
    }

    @Test
    public void testKeystoreWithInvalidPassword()
    {
        assertThatThrownBy(() ->
                new KeystoreSecretProvider(new KeystoreSecretProviderConfig()
                        .setKeyStoreType("jks")
                        .setKeyStoreFilePath(keystoreFile.file().getAbsolutePath())
                        .setKeyStorePassword("invalid_password"))
                        .resolveSecretValue("key"))
                .hasMessageContaining("Failed PKCS12 integrity checking");
    }

    @Test
    public void testSecretKeySpecKeyEntryFormat()
            throws Exception
    {
        Path keystorePath = createSecretKeySpecKeyStore(
                Map.of("my-service.access.key", "AKIATEST"),
                "none");

        KeystoreSecretProvider provider = new KeystoreSecretProvider(new KeystoreSecretProviderConfig()
                .setKeyStoreType("JCEKS")
                .setKeyStoreFilePath(keystorePath.toString())
                .setKeyStorePassword("none"));

        assertThat(provider.resolveSecretValue("my-service.access.key")).isEqualTo("AKIATEST");
    }

    @Test
    public void testSecretKeySpecKeyEntryFormatWithDifferentStoreAndEntryPasswords()
            throws Exception
    {
        String storePassword = "store_password";
        String entryPassword = "entry_password";
        Path keystorePath = createSecretKeySpecKeyStore(
                Map.of("my-service.access.key", "AKIATEST"),
                storePassword,
                entryPassword);

        KeystoreSecretProvider provider = new KeystoreSecretProvider(new KeystoreSecretProviderConfig()
                .setKeyStoreType("JCEKS")
                .setKeyStoreFilePath(keystorePath.toString())
                .setKeyStorePassword(storePassword)
                .setKeyStoreEntryPassword(entryPassword));

        assertThat(provider.resolveSecretValue("my-service.access.key")).isEqualTo("AKIATEST");
    }

    @Test
    public void testPbeKeyEntryFormatWithDifferentStoreAndEntryPasswords()
            throws Exception
    {
        String storePassword = "store_password";
        String entryPassword = "entry_password";
        Path keystorePath = createPbeKeyStore("key", "value", storePassword, entryPassword);

        KeystoreSecretProvider provider = new KeystoreSecretProvider(new KeystoreSecretProviderConfig()
                .setKeyStoreType("pkcs12")
                .setKeyStoreFilePath(keystorePath.toString())
                .setKeyStorePassword(storePassword)
                .setKeyStoreEntryPassword(entryPassword));

        assertThat(provider.resolveSecretValue("key")).isEqualTo("value");
    }

    @Test
    public void testResolveSecretValueNormalizesAliasToLowerCase()
            throws Exception
    {
        Path keystorePath = createSecretKeySpecKeyStore(
                Map.of("my-service.access.key", "AKIATEST"),
                "none");

        KeystoreSecretProvider provider = new KeystoreSecretProvider(new KeystoreSecretProviderConfig()
                .setKeyStoreType("JCEKS")
                .setKeyStoreFilePath(keystorePath.toString())
                .setKeyStorePassword("none"));

        assertThat(provider.resolveSecretValue("My-Service.Access.Key")).isEqualTo("AKIATEST");
    }

    @Test
    public void testMixedFormatKeystore()
            throws Exception
    {
        String password = "password";
        Path keystorePath = createMixedFormatKeyStore(
                Map.of("hadoop.alias", "hadoop-secret"),
                Map.of("pbe.alias", "pbe-secret"),
                password);

        KeystoreSecretProvider provider = new KeystoreSecretProvider(new KeystoreSecretProviderConfig()
                .setKeyStoreType("JCEKS")
                .setKeyStoreFilePath(keystorePath.toString())
                .setKeyStorePassword(password));

        assertThat(provider.resolveSecretValue("hadoop.alias")).isEqualTo("hadoop-secret");
        assertThat(provider.resolveSecretValue("pbe.alias")).isEqualTo("pbe-secret");
    }

    @Test
    public void testWrongEntryPasswordForSecretKeySpecEntry()
            throws Exception
    {
        String storePassword = "store_password";
        String entryPassword = "entry_password";
        Path keystorePath = createSecretKeySpecKeyStore(
                Map.of("my-service.access.key", "AKIATEST"),
                storePassword,
                entryPassword);

        KeystoreSecretProvider provider = new KeystoreSecretProvider(new KeystoreSecretProviderConfig()
                .setKeyStoreType("JCEKS")
                .setKeyStoreFilePath(keystorePath.toString())
                .setKeyStorePassword(storePassword)
                .setKeyStoreEntryPassword("wrong_entry_password"));

        assertThatThrownBy(() -> provider.resolveSecretValue("my-service.access.key"))
                .isInstanceOf(RuntimeException.class)
                .hasCauseInstanceOf(GeneralSecurityException.class);
    }

    @Test
    public void testWrongEntryPasswordForPbeEntry()
            throws Exception
    {
        String storePassword = "store_password";
        String entryPassword = "entry_password";
        Path keystorePath = createPbeKeyStore("key", "value", storePassword, entryPassword);

        KeystoreSecretProvider provider = new KeystoreSecretProvider(new KeystoreSecretProviderConfig()
                .setKeyStoreType("pkcs12")
                .setKeyStoreFilePath(keystorePath.toString())
                .setKeyStorePassword(storePassword)
                .setKeyStoreEntryPassword("wrong_entry_password"));

        assertThatThrownBy(() -> provider.resolveSecretValue("key"))
                .isInstanceOf(RuntimeException.class)
                .hasCauseInstanceOf(GeneralSecurityException.class);
    }

    @Test
    public void testResolveMultipleS3StyleAliases()
            throws Exception
    {
        Path keystorePath = createSecretKeySpecKeyStore(
                Map.of(
                        "fs.s3a.bucket.bucket-a.access.key", "AKIATEST",
                        "fs.s3a.bucket.bucket-a.secret.key", "SECRETTEST"),
                "none");

        KeystoreSecretProvider provider = new KeystoreSecretProvider(new KeystoreSecretProviderConfig()
                .setKeyStoreType("JCEKS")
                .setKeyStoreFilePath(keystorePath.toString())
                .setKeyStorePassword("none"));

        assertThat(provider.resolveSecretValue("fs.s3a.bucket.bucket-a.access.key")).isEqualTo("AKIATEST");
        assertThat(provider.resolveSecretValue("fs.s3a.bucket.bucket-a.secret.key")).isEqualTo("SECRETTEST");
    }

    @Test
    public void testEntryPasswordDefaultsToStorePasswordWhenUnset()
            throws Exception
    {
        String password = "same_password";
        Path keystorePath = createSecretKeySpecKeyStore(
                Map.of("my-service.access.key", "AKIATEST"),
                password);

        KeystoreSecretProvider provider = new KeystoreSecretProvider(new KeystoreSecretProviderConfig()
                .setKeyStoreType("JCEKS")
                .setKeyStoreFilePath(keystorePath.toString())
                .setKeyStorePassword(password));

        assertThat(provider.resolveSecretValue("my-service.access.key")).isEqualTo("AKIATEST");
    }

    private static Path createSecretKeySpecKeyStore(Map<String, String> aliases, String password)
            throws Exception
    {
        return createSecretKeySpecKeyStore(aliases, password, password);
    }

    private static Path createSecretKeySpecKeyStore(Map<String, String> aliases, String storePassword, String entryPassword)
            throws Exception
    {
        KeyStore keyStore = KeyStore.getInstance("JCEKS");
        keyStore.load(null, storePassword.toCharArray());
        for (Map.Entry<String, String> entry : aliases.entrySet()) {
            String alias = entry.getKey().toLowerCase(Locale.US);
            keyStore.setKeyEntry(
                    alias,
                    new SecretKeySpec(entry.getValue().getBytes(UTF_8), "AES"),
                    entryPassword.toCharArray(),
                    null);
        }

        Path keystorePath = Files.createTempFile("secret-key-spec-keystore", ".jceks");
        keystorePath.toFile().deleteOnExit();
        try (FileOutputStream outputStream = new FileOutputStream(keystorePath.toFile())) {
            keyStore.store(outputStream, storePassword.toCharArray());
        }
        return keystorePath;
    }

    private static Path createPbeKeyStore(String alias, String secretValue, String storePassword, String entryPassword)
            throws Exception
    {
        KeyStore keyStore = KeyStore.getInstance("pkcs12");
        keyStore.load(null, storePassword.toCharArray());

        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBE");
        keyStore.setEntry(
                alias,
                new KeyStore.SecretKeyEntry(factory.generateSecret(new PBEKeySpec(secretValue.toCharArray()))),
                new KeyStore.PasswordProtection(entryPassword.toCharArray()));

        Path keystorePath = Files.createTempFile("pbe-keystore", ".p12");
        keystorePath.toFile().deleteOnExit();
        try (FileOutputStream outputStream = new FileOutputStream(keystorePath.toFile())) {
            keyStore.store(outputStream, storePassword.toCharArray());
        }
        return keystorePath;
    }

    private static Path createMixedFormatKeyStore(
            Map<String, String> secretKeySpecAliases,
            Map<String, String> pbeAliases,
            String password)
            throws Exception
    {
        KeyStore keyStore = KeyStore.getInstance("JCEKS");
        keyStore.load(null, password.toCharArray());

        for (Map.Entry<String, String> entry : secretKeySpecAliases.entrySet()) {
            String alias = entry.getKey().toLowerCase(Locale.US);
            keyStore.setKeyEntry(
                    alias,
                    new SecretKeySpec(entry.getValue().getBytes(UTF_8), "AES"),
                    password.toCharArray(),
                    null);
        }

        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBE");
        for (Map.Entry<String, String> entry : pbeAliases.entrySet()) {
            keyStore.setEntry(
                    entry.getKey(),
                    new KeyStore.SecretKeyEntry(factory.generateSecret(new PBEKeySpec(entry.getValue().toCharArray()))),
                    new KeyStore.PasswordProtection(password.toCharArray()));
        }

        Path keystorePath = Files.createTempFile("mixed-format-keystore", ".jceks");
        keystorePath.toFile().deleteOnExit();
        try (FileOutputStream outputStream = new FileOutputStream(keystorePath.toFile())) {
            keyStore.store(outputStream, password.toCharArray());
        }
        return keystorePath;
    }
}
