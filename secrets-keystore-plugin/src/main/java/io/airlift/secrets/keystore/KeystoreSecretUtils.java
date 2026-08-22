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

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.KeyStore;
import java.security.KeyStore.PasswordProtection;
import java.security.KeyStore.SecretKeyEntry;

import static java.nio.charset.StandardCharsets.UTF_8;

final class KeystoreSecretUtils
{
    private KeystoreSecretUtils() {}

    static String readSecretValue(KeyStore keyStore, String alias, char[] keystorePassword, char[] entryPassword)
            throws GeneralSecurityException
    {
        try {
            // KeyStore setKeyEntry format: UTF-8 credential bytes in a SecretKeySpec
            if (keyStore.isKeyEntry(alias)) {
                Key key = keyStore.getKey(alias, keystorePassword);
                if (key instanceof SecretKeySpec secretKeySpec) {
                    return new String(secretKeySpec.getEncoded(), UTF_8);
                }
            }

            // PBE-protected SecretKeyEntry format (setEntry + getEntry)
            KeyStore.Entry entry = keyStore.getEntry(alias, new PasswordProtection(entryPassword));
            if (!(entry instanceof SecretKeyEntry secretKeyEntry)) {
                throw new RuntimeException("Unsupported keystore entry format for alias: " + alias);
            }
            SecretKeyFactory factory = SecretKeyFactory.getInstance(secretKeyEntry.getSecretKey().getAlgorithm());
            PBEKeySpec keySpec = (PBEKeySpec) factory.getKeySpec(secretKeyEntry.getSecretKey(), PBEKeySpec.class);
            return new String(keySpec.getPassword());
        }
        catch (ClassCastException e) {
            throw new RuntimeException("Unsupported keystore entry format for alias: " + alias, e);
        }
    }
}
