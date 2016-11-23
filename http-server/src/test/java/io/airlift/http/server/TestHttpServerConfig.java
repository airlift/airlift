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

import com.google.common.collect.ImmutableMap;
import io.airlift.configuration.testing.ConfigAssertions;
import io.airlift.units.DataSize;
import io.airlift.units.Duration;
import org.testng.annotations.Test;

import java.util.Map;
import java.util.concurrent.TimeUnit;

public class TestHttpServerConfig
{
    @Test
    public void testDefaults()
    {
        ConfigAssertions.assertRecordedDefaults(ConfigAssertions.recordDefaults(HttpServerConfig.class)
                .setHttpEnabled(true)
                .setHttpPort(8080)
                .setHttpAcceptQueueSize(8000)
                .setHttpsEnabled(false)
                .setHttpsPort(8443)
                .setSecureRandomAlgorithm(null)
                .setHttpsIncludedCipherSuites("")
                .setHttpsExcludedCipherSuites("")
                .setKeystorePath(null)
                .setKeystorePassword(null)
                .setLogPath("var/log/http-request.log")
                .setLogEnabled(true)
                .setLogMaxFileSize(new DataSize(Long.MAX_VALUE, DataSize.Unit.BYTE))
                .setLogHistory(15)
                .setHttpAcceptorThreads(null)
                .setHttpSelectorThreads(null)
                .setHttpsAcceptorThreads(null)
                .setHttpsSelectorThreads(null)
                .setMinThreads(2)
                .setMaxThreads(200)
                .setThreadMaxIdleTime(new Duration(1, TimeUnit.MINUTES))
                .setNetworkMaxIdleTime(new Duration(200, TimeUnit.SECONDS))
                .setUserAuthFile(null)
                .setAdminEnabled(true)
                .setAdminPort(0)
                .setAdminMinThreads(2)
                .setAdminMaxThreads(200)
                .setMaxRequestHeaderSize(null)
                .setHttp2MaxConcurrentStreams(16384)
                .setShowStackTrace(true)
        );
    }

    @Test
    public void testExplicitPropertyMappings()
    {
        Map<String, String> properties = new ImmutableMap.Builder<String, String>()
                .put("http-server.http.enabled", "false")
                .put("http-server.http.port", "1")
                .put("http-server.accept-queue-size", "1024")
                .put("http-server.https.enabled", "true")
                .put("http-server.https.port", "2")
                .put("http-server.https.keystore.path", "/keystore")
                .put("http-server.https.keystore.key", "keystore password")
                .put("http-server.https.secure-random-algorithm", "NativePRNG")
                .put("http-server.https.included-cipher", "TLS_RSA_WITH_AES_128_CBC_SHA,TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA")
                .put("http-server.https.excluded-cipher", "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256,TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA")
                .put("http-server.log.path", "/log")
                .put("http-server.log.enabled", "false")
                .put("http-server.log.max-size", "1GB")
                .put("http-server.log.max-history", "1")
                .put("http-server.http.acceptor-threads", "10")
                .put("http-server.http.selector-threads", "11")
                .put("http-server.https.acceptor-threads", "12")
                .put("http-server.https.selector-threads", "13")
                .put("http-server.threads.min", "100")
                .put("http-server.threads.max", "500")
                .put("http-server.threads.max-idle-time", "10m")
                .put("http-server.net.max-idle-time", "20m")
                .put("http-server.auth.users-file", "/auth")
                .put("http-server.admin.enabled", "false")
                .put("http-server.admin.port", "3")
                .put("http-server.admin.threads.min", "3")
                .put("http-server.admin.threads.max", "4")
                .put("http-server.max-request-header-size", "32kB")
                .put("http-server.http2.max-concurrent-streams", "1234")
                .put("http-server.show-stack-trace", "false")
                .build();

        HttpServerConfig expected = new HttpServerConfig()
                .setHttpEnabled(false)
                .setHttpPort(1)
                .setHttpAcceptQueueSize(1024)
                .setHttpsEnabled(true)
                .setHttpsPort(2)
                .setHttpsIncludedCipherSuites("TLS_RSA_WITH_AES_128_CBC_SHA,TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA")
                .setHttpsExcludedCipherSuites("TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256,TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA")
                .setKeystorePath("/keystore")
                .setKeystorePassword("keystore password")
                .setSecureRandomAlgorithm("NativePRNG")
                .setLogPath("/log")
                .setLogEnabled(false)
                .setLogMaxFileSize(new DataSize(1, DataSize.Unit.GIGABYTE))
                .setLogHistory(1)
                .setHttpAcceptorThreads(10)
                .setHttpSelectorThreads(11)
                .setHttpsAcceptorThreads(12)
                .setHttpsSelectorThreads(13)
                .setMinThreads(100)
                .setMaxThreads(500)
                .setThreadMaxIdleTime(new Duration(10, TimeUnit.MINUTES))
                .setNetworkMaxIdleTime(new Duration(20, TimeUnit.MINUTES))
                .setMaxRequestHeaderSize(new DataSize(32, DataSize.Unit.KILOBYTE))
                .setUserAuthFile("/auth")
                .setAdminEnabled(false)
                .setAdminPort(3)
                .setAdminMinThreads(3)
                .setAdminMaxThreads(4)
                .setHttp2MaxConcurrentStreams(1234)
                .setShowStackTrace(false);

        ConfigAssertions.assertFullMapping(properties, expected);
    }
}
