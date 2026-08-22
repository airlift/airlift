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

import com.google.inject.Inject;
import io.airlift.spi.secrets.SecretProvider;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.Locale;

import static io.airlift.secrets.keystore.KeystoreSecretUtils.readSecretValue;
import static java.util.Objects.requireNonNull;

public class KeystoreSecretProvider
        implements SecretProvider
{
    private final KeyStore keyStore;
    private final char[] keystorePassword;
    private final char[] entryPassword;

    @Inject
    public KeystoreSecretProvider(KeystoreSecretProviderConfig config)
            throws GeneralSecurityException, IOException
    {
        requireNonNull(config, "config is null");
        String configuredKeystorePassword = config.getKeyStorePassword();
        keystorePassword = configuredKeystorePassword.toCharArray();
        String configuredEntryPassword = config.getKeyStoreEntryPassword() != null
                ? config.getKeyStoreEntryPassword()
                : configuredKeystorePassword;
        entryPassword = configuredEntryPassword.toCharArray();

        keyStore = KeyStore.getInstance(config.getKeyStoreType());
        try (InputStream stream = new FileInputStream(config.getKeyStoreFilePath())) {
            keyStore.load(stream, keystorePassword);
        }
    }

    @Override
    public String resolveSecretValue(String key)
    {
        try {
            String alias = key.toLowerCase(Locale.US);
            if (!keyStore.containsAlias(alias)) {
                throw new RuntimeException("Key not found in keystore: " + key);
            }
            return readSecretValue(keyStore, alias, keystorePassword, entryPassword);
        }
        catch (GeneralSecurityException e) {
            throw new RuntimeException(e);
        }
    }
}
