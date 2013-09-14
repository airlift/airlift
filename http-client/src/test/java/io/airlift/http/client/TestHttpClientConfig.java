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
import io.airlift.units.Duration;
import org.testng.annotations.Test;

import javax.validation.constraints.NotNull;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static io.airlift.http.client.HttpClientConfig.JAVAX_NET_SSL_KEY_STORE;
import static io.airlift.http.client.HttpClientConfig.JAVAX_NET_SSL_KEY_STORE_PASSWORD;
import static io.airlift.testing.ValidationAssertions.assertFailsValidation;

public class TestHttpClientConfig
{
    @Test
    public void testDefaults()
    {
        ConfigAssertions.assertRecordedDefaults(ConfigAssertions.recordDefaults(HttpClientConfig.class)
                .setConnectTimeout(new Duration(1, TimeUnit.SECONDS))
                .setReadTimeout(new Duration(1, TimeUnit.MINUTES))
                .setKeepAliveInterval(null)
                .setMaxConnections(200)
                .setMaxConnectionsPerServer(20)
                .setSocksProxy(null)
                .setKeyStorePath(System.getProperty(JAVAX_NET_SSL_KEY_STORE))
                .setKeyStorePassword(System.getProperty(JAVAX_NET_SSL_KEY_STORE_PASSWORD)));
    }

    @Test
    public void testExplicitPropertyMappings()
    {
        Map<String, String> properties = new ImmutableMap.Builder<String, String>()
                .put("http-client.connect-timeout", "4s")
                .put("http-client.read-timeout", "5s")
                .put("http-client.keep-alive-interval", "6s")
                .put("http-client.max-connections", "12")
                .put("http-client.max-connections-per-server", "3")
                .put("http-client.socks-proxy", "localhost:1080")
                .put("http-client.key-store-path", "key-store")
                .put("http-client.key-store-password", "key-store-password")
                .build();

        HttpClientConfig expected = new HttpClientConfig()
                .setConnectTimeout(new Duration(4, TimeUnit.SECONDS))
                .setReadTimeout(new Duration(5, TimeUnit.SECONDS))
                .setKeepAliveInterval(new Duration(6, TimeUnit.SECONDS))
                .setMaxConnections(12)
                .setMaxConnectionsPerServer(3)
                .setSocksProxy(HostAndPort.fromParts("localhost", 1080))
                .setKeyStorePath("key-store")
                .setKeyStorePassword("key-store-password");

        ConfigAssertions.assertFullMapping(properties, expected);
    }

    @Test
    public void testValidations()
    {
        assertFailsValidation(new HttpClientConfig().setConnectTimeout(null), "connectTimeout", "may not be null", NotNull.class);
        assertFailsValidation(new HttpClientConfig().setReadTimeout(null), "readTimeout", "may not be null", NotNull.class);
    }
}
