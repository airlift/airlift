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
package com.proofpoint.http.client;

import com.google.common.collect.ImmutableMap;
import com.google.common.net.HostAndPort;
import com.proofpoint.units.DataSize;
import com.proofpoint.units.DataSize.Unit;
import com.proofpoint.units.Duration;
import org.testng.annotations.Test;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.proofpoint.configuration.testing.ConfigAssertions.assertFullMapping;
import static com.proofpoint.configuration.testing.ConfigAssertions.assertLegacyEquivalence;
import static com.proofpoint.configuration.testing.ConfigAssertions.assertRecordedDefaults;
import static com.proofpoint.configuration.testing.ConfigAssertions.recordDefaults;
import static com.proofpoint.http.client.HttpClientConfig.JAVAX_NET_SSL_KEY_STORE;
import static com.proofpoint.http.client.HttpClientConfig.JAVAX_NET_SSL_KEY_STORE_PASSWORD;
import static com.proofpoint.testing.ValidationAssertions.assertFailsValidation;

public class TestHttpClientConfig
{
    @Test
    public void testDefaults()
    {
        assertRecordedDefaults(recordDefaults(HttpClientConfig.class)
                .setConnectTimeout(new Duration(1, TimeUnit.SECONDS))
                .setRequestTimeout(null)
                .setIdleTimeout(new Duration(1, TimeUnit.MINUTES))
                .setKeepAliveInterval(null)
                .setMaxConnections(200)
                .setMaxConnectionsPerServer(20)
                .setMaxRequestsQueuedPerDestination(20)
                .setMaxContentLength(new DataSize(16, Unit.MEGABYTE))
                .setSocksProxy(null)
                .setKeyStorePath(System.getProperty(JAVAX_NET_SSL_KEY_STORE))
                .setKeyStorePassword(System.getProperty(JAVAX_NET_SSL_KEY_STORE_PASSWORD)));
    }

    @Test
    public void testExplicitPropertyMappings()
    {
        Map<String, String> properties = new ImmutableMap.Builder<String, String>()
                .put("http-client.connect-timeout", "4s")
                .put("http-client.request-timeout", "15s")
                .put("http-client.idle-timeout", "5s")
                .put("http-client.keep-alive-interval", "6s")
                .put("http-client.max-connections", "12")
                .put("http-client.max-connections-per-server", "3")
                .put("http-client.max-requests-queued-per-destination", "10")
                .put("http-client.max-content-length", "1MB")
                .put("http-client.socks-proxy", "localhost:1080")
                .put("http-client.key-store-path", "key-store")
                .put("http-client.key-store-password", "key-store-password")
                .build();

        HttpClientConfig expected = new HttpClientConfig()
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
                .setKeyStorePassword("key-store-password");

        assertFullMapping(properties, expected);
    }

    @Test
    public void testLegacyProperties()
    {
        Map<String, String> currentProperties = new ImmutableMap.Builder<String, String>()
                .put("http-client.idle-timeout", "111m")
                .build();

        Map<String, String> oldProperties = new ImmutableMap.Builder<String, String>()
                .put("http-client.read-timeout", "111m")
                .build();

        assertLegacyEquivalence(HttpClientConfig.class, currentProperties, oldProperties);
    }

    @Test
    public void testValidations()
    {
        assertFailsValidation(new HttpClientConfig().setConnectTimeout(null), "connectTimeout", "may not be null", NotNull.class);
        assertFailsValidation(new HttpClientConfig().setIdleTimeout(null), "idleTimeout", "may not be null", NotNull.class);
        assertFailsValidation(new HttpClientConfig().setMaxConnections(0), "maxConnections", "must be greater than or equal to 1", Min.class);
        assertFailsValidation(new HttpClientConfig().setMaxConnectionsPerServer(0), "maxConnectionsPerServer", "must be greater than or equal to 1", Min.class);
        assertFailsValidation(new HttpClientConfig().setMaxRequestsQueuedPerDestination(-1), "maxRequestsQueuedPerDestination", "must be greater than or equal to 0", Min.class);
        assertFailsValidation(new HttpClientConfig().setMaxContentLength(null), "maxContentLength", "may not be null", NotNull.class);
    }
}
