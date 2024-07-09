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

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

import java.io.FileInputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;

public class KeystoreSecretProvider
        implements SecretProvider
{
    private final KeyStore keyStore;
    private final char[] keystorePassword;

    @Inject
    public KeystoreSecretProvider(KeystoreSecretProviderConfig config)
            throws GeneralSecurityException, IOException
    {
        keystorePassword = config.getKeyStorePassword().toCharArray();
        keyStore = KeyStore.getInstance(config.getKeyStoreType());
        keyStore.load(new FileInputStream(config.getKeyStoreFilePath()), keystorePassword);
    }

    @Override
    public String resolveSecretValue(String key)
    {
        try {
            KeyStore.SecretKeyEntry secretKeyEntry = (KeyStore.SecretKeyEntry) keyStore.getEntry(key, new KeyStore.PasswordProtection(keystorePassword));

            if (secretKeyEntry == null) {
                throw new RuntimeException("Key not found in keystore: " + key);
            }
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBE");
            PBEKeySpec keySpec = (PBEKeySpec) factory.getKeySpec(secretKeyEntry.getSecretKey(), PBEKeySpec.class);
            return new String(keySpec.getPassword());
        }
        catch (GeneralSecurityException e) {
            throw new RuntimeException(e);
        }
    }
}
