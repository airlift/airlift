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
package io.airlift.http.client;

import com.google.common.collect.ImmutableMap;
import com.google.common.net.HostAndPort;
import io.airlift.configuration.testing.ConfigAssertions;
import io.airlift.units.DataSize;
import io.airlift.units.DataSize.Unit;
import io.airlift.units.Duration;
import org.testng.annotations.Test;

import javax.validation.constraints.NotNull;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static io.airlift.http.client.HttpClientConfig.JAVAX_NET_SSL_KEY_STORE;
import static io.airlift.http.client.HttpClientConfig.JAVAX_NET_SSL_KEY_STORE_PASSWORD;
import static io.airlift.http.client.HttpClientConfig.JAVAX_NET_SSL_TRUST_STORE;
import static io.airlift.http.client.HttpClientConfig.JAVAX_NET_SSL_TRUST_STORE_PASSWORD;
import static io.airlift.testing.ValidationAssertions.assertFailsValidation;

@SuppressWarnings("deprecation")
public class TestHttpClientConfig
{
    @Test
    public void testDefaults()
    {
        ConfigAssertions.assertRecordedDefaults(ConfigAssertions.recordDefaults(HttpClientConfig.class)
                .setHttp2Enabled(false)
                .setConnectTimeout(new Duration(1, TimeUnit.SECONDS))
                .setRequestTimeout(new Duration(5, TimeUnit.MINUTES))
                .setIdleTimeout(new Duration(1, TimeUnit.MINUTES))
                .setKeepAliveInterval(null)
                .setMaxConnections(200)
                .setMaxConnectionsPerServer(20)
                .setMaxRequestsQueuedPerDestination(1024)
                .setMaxContentLength(new DataSize(16, Unit.MEGABYTE))
                .setSocksProxy(null)
                .setKeyStorePath(System.getProperty(JAVAX_NET_SSL_KEY_STORE))
                .setKeyStorePassword(System.getProperty(JAVAX_NET_SSL_KEY_STORE_PASSWORD))
                .setTrustStorePath(System.getProperty(JAVAX_NET_SSL_TRUST_STORE))
                .setTrustStorePassword(System.getProperty(JAVAX_NET_SSL_TRUST_STORE_PASSWORD))
                .setSecureRandomAlgorithm(null)
                .setAuthenticationEnabled(false)
                .setKerberosRemoteServiceName(null)
                .setKerberosPrincipal(null));
    }

    @Test
    public void testExplicitPropertyMappings()
    {
        Map<String, String> properties = new ImmutableMap.Builder<String, String>()
                .put("http-client.http2.enabled", "true")
                .put("http-client.connect-timeout", "4s")
                .put("http-client.request-timeout", "15s")
                .put("http-client.idle-timeout", "5s")
                .put("http-client.keep-alive-interval", "6s")
                .put("http-client.max-connections", "12")
                .put("http-client.max-connections-per-server", "3")
                .put("http-client.max-requests-queued-per-destination", "10")
                .put("http-client.max-content-length", "1MB")
                .put("http-client.socks-proxy", "localhost:1080")
                .put("http-client.secure-random-algorithm", "NativePRNG")
                .put("http-client.key-store-path", "key-store")
                .put("http-client.key-store-password", "key-store-password")
                .put("http-client.trust-store-path", "trust-store")
                .put("http-client.trust-store-password", "trust-store-password")
                .put("http-client.authentication.enabled", "true")
                .put("http-client.authentication.krb5.remote-service-name", "airlift")
                .put("http-client.authentication.krb5.principal", "airlift-client")
                .build();

        HttpClientConfig expected = new HttpClientConfig()
                .setHttp2Enabled(true)
                .setConnectTimeout(new Duration(4, TimeUnit.SECONDS))
                .setRequestTimeout(new Duration(15, TimeUnit.SECONDS))
                .setIdleTimeout(new Duration(5, TimeUnit.SECONDS))
                .setKeepAliveInterval(new Duration(6, TimeUnit.SECONDS))
                .setMaxConnections(12)
                .setMaxConnectionsPerServer(3)
                .setMaxRequestsQueuedPerDestination(10)
                .setMaxContentLength(new DataSize(1, Unit.MEGABYTE))
                .setSocksProxy(HostAndPort.fromParts("localhost", 1080))
                .setKeyStorePath("key-store")
                .setKeyStorePassword("key-store-password")
                .setTrustStorePath("trust-store")
                .setTrustStorePassword("trust-store-password")
                .setSecureRandomAlgorithm("NativePRNG")
                .setAuthenticationEnabled(true)
                .setKerberosRemoteServiceName("airlift")
                .setKerberosPrincipal("airlift-client");

        ConfigAssertions.assertFullMapping(properties, expected);
    }

    @Test
    public void testDeprecatedProperties()
    {
        Map<String, String> currentProperties = new ImmutableMap.Builder<String, String>()
                .put("http-client.idle-timeout", "111m")
                .build();

        Map<String, String> oldProperties = new ImmutableMap.Builder<String, String>()
                .put("http-client.read-timeout", "111m")
                .build();

        ConfigAssertions.assertDeprecatedEquivalence(HttpClientConfig.class, currentProperties, oldProperties);
    }

    @Test
    public void testValidations()
    {
        assertFailsValidation(new HttpClientConfig().setConnectTimeout(null), "connectTimeout", "may not be null", NotNull.class);
        assertFailsValidation(new HttpClientConfig().setRequestTimeout(null), "requestTimeout", "may not be null", NotNull.class);
        assertFailsValidation(new HttpClientConfig().setIdleTimeout(null), "idleTimeout", "may not be null", NotNull.class);
    }
}
