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
import com.google.inject.ConfigurationException;
import io.airlift.configuration.testing.ConfigAssertions;
import io.airlift.units.DataSize;
import io.airlift.units.Duration;
import jakarta.validation.constraints.NotNull;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static io.airlift.http.client.HttpClientConfig.JAVAX_NET_SSL_KEY_STORE;
import static io.airlift.http.client.HttpClientConfig.JAVAX_NET_SSL_KEY_STORE_PASSWORD;
import static io.airlift.http.client.HttpClientConfig.JAVAX_NET_SSL_TRUST_STORE;
import static io.airlift.http.client.HttpClientConfig.JAVAX_NET_SSL_TRUST_STORE_PASSWORD;
import static io.airlift.testing.ValidationAssertions.assertFailsValidation;
import static io.airlift.units.DataSize.Unit.GIGABYTE;
import static io.airlift.units.DataSize.Unit.KILOBYTE;
import static io.airlift.units.DataSize.Unit.MEGABYTE;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class TestHttpClientConfig
{
    @Test
    public void testDefaults()
    {
        ConfigAssertions.assertRecordedDefaults(ConfigAssertions.recordDefaults(HttpClientConfig.class)
                .setVerifyHostname(true)
                .setHttp2Enabled(false)
                .setConnectTimeout(new Duration(5, SECONDS))
                .setRequestTimeout(new Duration(5, MINUTES))
                .setIdleTimeout(new Duration(1, MINUTES))
                .setDestinationIdleTimeout(new Duration(1, MINUTES))
                .setMaxConnectionsPerServer(20)
                .setMaxRequestsQueuedPerDestination(1024)
                .setMaxContentLength(DataSize.of(16, MEGABYTE))
                .setRequestBufferSize(DataSize.of(4, KILOBYTE))
                .setResponseBufferSize(DataSize.of(16, KILOBYTE))
                .setMaxRequestHeaderSize(DataSize.of(8, KILOBYTE))
                .setMaxResponseHeaderSize(DataSize.of(16, KILOBYTE))
                .setMaxHeapMemory(null)
                .setMaxDirectMemory(null)
                .setSocksProxy(null)
                .setHttpProxy(null)
                .setHttpProxyUser(null)
                .setHttpProxyPassword(null)
                .setSecureProxy(false)
                .setKeyStorePath(System.getProperty(JAVAX_NET_SSL_KEY_STORE))
                .setKeyStorePassword(System.getProperty(JAVAX_NET_SSL_KEY_STORE_PASSWORD))
                .setTrustStorePath(System.getProperty(JAVAX_NET_SSL_TRUST_STORE))
                .setTrustStorePassword(System.getProperty(JAVAX_NET_SSL_TRUST_STORE_PASSWORD))
                .setSecureRandomAlgorithm(null)
                .setHttpsIncludedCipherSuites("")
                .setHttpsExcludedCipherSuites(String.join(",", getJettyDefaultExcludedCiphers()))
                .setAutomaticHttpsSharedSecret(null)
                .setHttp2InitialSessionReceiveWindowSize(DataSize.of(16, MEGABYTE))
                .setHttp2InitialStreamReceiveWindowSize(DataSize.of(16, MEGABYTE))
                .setHttp2InputBufferSize(DataSize.of(8, KILOBYTE))
                .setSelectorCount(2)
                .setRecordRequestComplete(true)
                .setConnectBlocking(false)
                .setMaxThreads(200)
                .setMinThreads(8)
                .setTimeoutConcurrency(1)
                .setTimeoutThreads(1)
                .setLogEnabled(false)
                .setLogHistory(15)
                .setLogMaxFileSize(DataSize.of(1, GIGABYTE))
                .setLogPath("var/log/")
                .setLogQueueSize(10_000)
                .setLogBufferSize(DataSize.of(1, MEGABYTE))
                .setLogFlushInterval(new Duration(10, SECONDS))
                .setLogCompressionEnabled(true)
                .setTcpKeepAliveIdleTime(null)
                .setStrictEventOrdering(false)
                .setUseVirtualThreads(false));
    }

    @Test
    public void testExplicitPropertyMappings()
    {
        Map<String, String> properties = new ImmutableMap.Builder<String, String>()
                .put("http-client.https.hostname-verification", "false")
                .put("http-client.http2.enabled", "true")
                .put("http-client.connect-timeout", "4s")
                .put("http-client.request-timeout", "15s")
                .put("http-client.idle-timeout", "5s")
                .put("http-client.destination-idle-timeout", "10s")
                .put("http-client.max-connections-per-server", "3")
                .put("http-client.max-requests-queued-per-destination", "10")
                .put("http-client.max-content-length", "1MB")
                .put("http-client.request-buffer-size", "42kB")
                .put("http-client.response-buffer-size", "43kB")
                .put("http-client.max-request-header-size", "16kB")
                .put("http-client.max-response-header-size", "32kB")
                .put("http-client.max-heap-memory", "1337MB")
                .put("http-client.max-direct-memory", "2137MB")
                .put("http-client.socks-proxy", "localhost:1080")
                .put("http-client.http-proxy", "localhost:8080")
                .put("http-client.http-proxy.user", "user")
                .put("http-client.http-proxy.password", "pass")
                .put("http-client.http-proxy.secure", "true")
                .put("http-client.secure-random-algorithm", "NativePRNG")
                .put("http-client.https.included-cipher", "TLS_RSA_WITH_AES_128_CBC_SHA,TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA")
                .put("http-client.https.excluded-cipher", "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256,TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA")
                .put("http-client.https.automatic-shared-secret", "automatic-secret")
                .put("http-client.key-store-path", "key-store")
                .put("http-client.key-store-password", "key-store-password")
                .put("http-client.trust-store-path", "trust-store")
                .put("http-client.trust-store-password", "trust-store-password")
                .put("http-client.http2.session-receive-window-size", "7MB")
                .put("http-client.http2.stream-receive-window-size", "7MB")
                .put("http-client.http2.input-buffer-size", "1MB")
                .put("http-client.selector-count", "16")
                .put("http-client.record-request-complete", "false")
                .put("http-client.use-blocking-connect", "true")
                .put("http-client.max-threads", "33")
                .put("http-client.min-threads", "11")
                .put("http-client.timeout-concurrency", "33")
                .put("http-client.timeout-threads", "44")
                .put("http-client.log.enabled", "true")
                .put("http-client.log.max-history", "22")
                .put("http-client.log.max-size", "2GB")
                .put("http-client.log.path", "/tmp/log/")
                .put("http-client.log.queue-size", "12345")
                .put("http-client.log.buffer-size", "3MB")
                .put("http-client.log.flush-interval", "99s")
                .put("http-client.log.compression.enabled", "false")
                .put("http-client.tcp-keep-alive-idle-time", "1m")
                .put("http-client.strict-event-ordering", "true")
                .put("http-client.use-virtual-threads", "true")
                .build();

        HttpClientConfig expected = new HttpClientConfig()
                .setVerifyHostname(false)
                .setHttp2Enabled(true)
                .setConnectTimeout(new Duration(4, SECONDS))
                .setRequestTimeout(new Duration(15, SECONDS))
                .setIdleTimeout(new Duration(5, SECONDS))
                .setDestinationIdleTimeout(new Duration(10, SECONDS))
                .setMaxConnectionsPerServer(3)
                .setMaxRequestsQueuedPerDestination(10)
                .setMaxContentLength(DataSize.of(1, MEGABYTE))
                .setRequestBufferSize(DataSize.of(42, KILOBYTE))
                .setResponseBufferSize(DataSize.of(43, KILOBYTE))
                .setMaxRequestHeaderSize(DataSize.of(16, KILOBYTE))
                .setMaxResponseHeaderSize(DataSize.of(32, KILOBYTE))
                .setMaxHeapMemory(DataSize.of(1337, MEGABYTE))
                .setMaxDirectMemory(DataSize.of(2137, MEGABYTE))
                .setSocksProxy(HostAndPort.fromParts("localhost", 1080))
                .setHttpProxy(HostAndPort.fromParts("localhost", 8080))
                .setHttpProxyUser("user")
                .setHttpProxyPassword("pass")
                .setSecureProxy(true)
                .setKeyStorePath("key-store")
                .setKeyStorePassword("key-store-password")
                .setTrustStorePath("trust-store")
                .setTrustStorePassword("trust-store-password")
                .setSecureRandomAlgorithm("NativePRNG")
                .setHttpsIncludedCipherSuites("TLS_RSA_WITH_AES_128_CBC_SHA,TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA")
                .setHttpsExcludedCipherSuites("TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256,TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA")
                .setAutomaticHttpsSharedSecret("automatic-secret")
                .setHttp2InitialSessionReceiveWindowSize(DataSize.of(7, MEGABYTE))
                .setHttp2InitialStreamReceiveWindowSize(DataSize.of(7, MEGABYTE))
                .setHttp2InputBufferSize(DataSize.of(1, MEGABYTE))
                .setSelectorCount(16)
                .setRecordRequestComplete(false)
                .setConnectBlocking(true)
                .setMaxThreads(33)
                .setMinThreads(11)
                .setTimeoutConcurrency(33)
                .setTimeoutThreads(44)
                .setLogEnabled(true)
                .setLogHistory(22)
                .setLogMaxFileSize(DataSize.of(2, GIGABYTE))
                .setLogPath("/tmp/log/")
                .setLogQueueSize(12345)
                .setLogBufferSize(DataSize.of(3, MEGABYTE))
                .setLogFlushInterval(new Duration(99, SECONDS))
                .setLogCompressionEnabled(false)
                .setTcpKeepAliveIdleTime(new Duration(1, MINUTES))
                .setStrictEventOrdering(true)
                .setUseVirtualThreads(true);

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
        assertFailsValidation(new HttpClientConfig().setConnectTimeout(null), "connectTimeout", "must not be null", NotNull.class);
        assertFailsValidation(new HttpClientConfig().setRequestTimeout(null), "requestTimeout", "must not be null", NotNull.class);
        assertFailsValidation(new HttpClientConfig().setIdleTimeout(null), "idleTimeout", "must not be null", NotNull.class);
    }

    @Test
    public void testInvalidProxyConfiguration()
    {
        HttpClientConfig clientConfig = new HttpClientConfig()
                .setSocksProxy(HostAndPort.fromParts("localhost", 1080))
                .setHttpProxy(HostAndPort.fromParts("localhost", 8080));
        assertThatThrownBy(clientConfig::validate)
                .isInstanceOf(ConfigurationException.class)
                .hasMessageContaining("Only one proxy can be configured for HttpClient");
    }

    @Test
    public void testInvalidHttpProxyConfiguration()
    {
        HttpClientConfig clientConfig = new HttpClientConfig().setSecureProxy(true);
        assertThatThrownBy(clientConfig::validate)
                .isInstanceOf(ConfigurationException.class)
                .hasMessageContaining("http-client.http-proxy.secure can be enabled only when http-client.http-proxy is set");
    }

    private List<String> getJettyDefaultExcludedCiphers()
    {
        SslContextFactory sslContextFactory = new SslContextFactory.Client();
        return Arrays.asList(sslContextFactory.getExcludeCipherSuites());
    }
}
