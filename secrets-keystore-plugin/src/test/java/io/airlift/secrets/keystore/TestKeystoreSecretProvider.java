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

import java.io.FileOutputStream;
import java.security.KeyStore;

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
    public void testConfigurationResolver()
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
}
