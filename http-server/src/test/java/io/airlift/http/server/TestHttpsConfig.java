/*
 * Copyright 2010 Proofpoint, Inc.
 *
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
package io.airlift.http.server;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.airlift.units.Duration;
import jakarta.validation.constraints.AssertTrue;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

import static io.airlift.configuration.testing.ConfigAssertions.assertFullMapping;
import static io.airlift.configuration.testing.ConfigAssertions.assertRecordedDefaults;
import static io.airlift.configuration.testing.ConfigAssertions.recordDefaults;
import static io.airlift.testing.ValidationAssertions.assertFailsValidation;
import static io.airlift.testing.ValidationAssertions.assertValidates;
import static java.lang.String.join;
import static java.util.concurrent.TimeUnit.HOURS;
import static java.util.concurrent.TimeUnit.MINUTES;

public class TestHttpsConfig
{
    @Test
    public void testDefaults()
    {
        assertRecordedDefaults(recordDefaults(HttpsConfig.class)
                .setHttpsPort(8443)
                .setSecureRandomAlgorithm(null)
                .setHttpsIncludedCipherSuites("")
                .setHttpsExcludedCipherSuites(join(",", getJettyDefaultExcludedCiphers()))
                .setSslSessionTimeout(new Duration(4, HOURS))
                .setSslSessionCacheSize(10_000)
                .setKeystorePath(null)
                .setKeystorePassword(null)
                .setKeyManagerPassword(null)
                .setTrustStorePath(null)
                .setTrustStorePassword(null)
                .setSslContextRefreshTime(new Duration(1, MINUTES))
                .setAutomaticHttpsSharedSecret(null)
                .setAutomaticHttpsSharedSecret(null));
    }

    @Test
    public void testExplicitPropertyMappings()
    {
        Map<String, String> properties = new ImmutableMap.Builder<String, String>()
                .put("http-server.https.port", "2")
                .put("http-server.https.keystore.path", "/keystore")
                .put("http-server.https.keystore.key", "keystore password")
                .put("http-server.https.keymanager.password", "keymanager password")
                .put("http-server.https.truststore.path", "/truststore")
                .put("http-server.https.truststore.key", "truststore password")
                .put("http-server.https.secure-random-algorithm", "NativePRNG")
                .put("http-server.https.included-cipher", "TLS_RSA_WITH_AES_128_CBC_SHA,TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA")
                .put("http-server.https.excluded-cipher", "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256,TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA")
                .put("http-server.https.ssl-session-timeout", "7h")
                .put("http-server.https.ssl-session-cache-size", "456")
                .put("http-server.https.ssl-context.refresh-time", "10m")
                .put("http-server.https.automatic-shared-secret", "automatic-secret")
                .build();

        HttpsConfig expected = new HttpsConfig()
                .setHttpsPort(2)
                .setHttpsIncludedCipherSuites("TLS_RSA_WITH_AES_128_CBC_SHA,TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA")
                .setHttpsExcludedCipherSuites("TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256,TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA")
                .setSslSessionTimeout(new Duration(7, HOURS))
                .setSslSessionCacheSize(456)
                .setKeystorePath("/keystore")
                .setKeystorePassword("keystore password")
                .setKeyManagerPassword("keymanager password")
                .setTrustStorePath("/truststore")
                .setTrustStorePassword("truststore password")
                .setSecureRandomAlgorithm("NativePRNG")
                .setSslContextRefreshTime(new Duration(10, MINUTES))
                .setAutomaticHttpsSharedSecret("automatic-secret");

        assertFullMapping(properties, expected);
    }

    @Test
    public void testHttpsConfigurationValidation()
    {
        assertValidates(
                new HttpsConfig()
                        .setKeystorePath("/test/keystore"));

        assertFailsValidation(
                new HttpsConfig(),
                "httpsConfigurationValid",
                "Keystore path or automatic HTTPS shared secret must be provided when HTTPS is enabled",
                AssertTrue.class);
    }

    private static List<String> getJettyDefaultExcludedCiphers()
    {
        return ImmutableList.copyOf(new SslContextFactory.Server().getExcludeCipherSuites());
    }
}
